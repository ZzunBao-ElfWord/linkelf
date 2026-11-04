package com.linksprite.explorer;

import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.client.protocol.model.JsonTransactionResponse;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosBlock;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosBlock.Block;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosBlock.TransactionResult;
import org.fisco.bcos.sdk.v3.client.protocol.response.BcosTransactionReceipt;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 轻量浏览器索引器。
 * <p>
 * 单进程内定时轮询链上区块，将区块/交易/日志/铸造与授权事件写入 MariaDB。
 * 每块一条事务，失败只告警并停留在该块，下一轮重试；异常不会让定时任务停止。
 * 未开启 explorer.enabled 时本 Bean 不会被创建。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "explorer", name = "enabled", havingValue = "true")
public class ExplorerIndexer {

    /** ERC721 转账事件 */
    private static final String TRANSFER_SIG = "Transfer(address,address,uint256)";
    /** 授权登记事件 */
    private static final String AUTH_REGISTERED_SIG =
            "AuthorizationRegistered(uint256,string,address,uint256,uint256,uint256)";
    /** NFT 铸造事件（用于把 tokenId 关联到 authId / 产品） */
    private static final String NFT_MINTED_SIG = "NFTMinted(uint256,uint8,address,uint256,string)";
    /** 零地址：Transfer 的来源为零地址即视为铸造 */
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";
    private static final String SYNC_KEY = "last_block";

    private final JdbcTemplate jdbc;
    private final ExplorerProperties properties;
    private final ObjectProvider<Client> clientProvider;
    private final TransactionTemplate transactionTemplate;

    /** 未连接链时只告警一次，避免刷屏 */
    private volatile boolean warnedNoClient;

    public ExplorerIndexer(JdbcTemplate jdbc,
                           ExplorerProperties properties,
                           ObjectProvider<Client> clientProvider,
                           TransactionTemplate explorerTransactionTemplate) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clientProvider = clientProvider;
        this.transactionTemplate = explorerTransactionTemplate;
    }

    /**
     * 定时轮询入口。fixedDelay 取自 explorer.poll-ms（默认 2000 毫秒）。
     */
    @Scheduled(fixedDelayString = "${explorer.poll-ms:2000}", initialDelayString = "${explorer.poll-ms:2000}")
    public void poll() {
        if (!properties.isEnabled()) {
            return;
        }
        Client client = clientProvider.getIfAvailable();
        if (client == null) {
            if (!warnedNoClient) {
                warnedNoClient = true;
                log.warn("浏览器索引器已启用，但链客户端未初始化（fisco.enabled=false），暂不索引");
            }
            return;
        }
        warnedNoClient = false;

        try {
            long latest = client.getBlockNumber().getBlockNumber().longValue();
            long last = resolveLastBlock(latest);
            if (latest <= last) {
                return;
            }
            // topic 按客户端加密类型（SM3/Keccak）计算，顺序：Transfer / AuthorizationRegistered / NFTMinted
            String[] topics = {
                    topicOf(client, TRANSFER_SIG),
                    topicOf(client, AUTH_REGISTERED_SIG),
                    topicOf(client, NFT_MINTED_SIG)
            };

            for (long number = last + 1; number <= latest; number++) {
                try {
                    indexBlock(client, number, topics);
                } catch (Exception e) {
                    // 停留在该块，不推进 eb_sync，下一轮重试
                    log.warn("区块 {} 索引失败，停留在该块等待下轮重试: {}", number, e.getMessage());
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("浏览器索引轮询失败（不影响定时任务继续运行）: {}", e.toString());
        }
    }

    /** 冷启动：从 max(0, latest - backfill) 开始；否则读取 eb_sync */
    private long resolveLastBlock(long latest) {
        long last = readLastBlock();
        if (last >= 0) {
            return last;
        }
        long first = Math.max(0, latest - Math.max(0, properties.getBackfillBlocks()));
        long baseline = first - 1;
        jdbc.update("INSERT INTO eb_sync(k, v) VALUES(?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)",
                SYNC_KEY, String.valueOf(baseline));
        log.info("浏览器冷启动，索引起点区块 {}（latest={}, backfill={}）",
                first, latest, properties.getBackfillBlocks());
        return baseline;
    }

    private long readLastBlock() {
        try {
            List<String> values = jdbc.queryForList(
                    "SELECT v FROM eb_sync WHERE k = ?", String.class, SYNC_KEY);
            if (values.isEmpty() || values.get(0) == null) {
                return -1L;
            }
            return Long.parseLong(values.get(0).trim());
        } catch (Exception e) {
            log.warn("读取索引进度失败，按冷启动处理: {}", e.getMessage());
            return -1L;
        }
    }

    /** 拉取并解析单个区块，然后在一个事务里写入全部数据 */
    private void indexBlock(Client client, long number, String[] eventTopics) {
        BcosBlock response = client.getBlockByNumber(BigInteger.valueOf(number), false, false);
        if (response == null || response.hasError() || response.getBlock() == null) {
            throw new IllegalStateException("获取区块失败: " + number);
        }
        Block block = response.getBlock();
        String parentHash = block.getParentInfo() == null || block.getParentInfo().isEmpty()
                ? null : block.getParentInfo().get(0).getBlockHash();
        String sealer = resolveSealer(block);
        long timestamp = block.getTimestamp();
        long gasUsed = parseHexLong(block.getGasUsed());

        List<TxRow> txRows = new ArrayList<>();
        List<LogRow> logRows = new ArrayList<>();
        List<TokenRow> tokenRows = new ArrayList<>();
        List<AuthRow> authRows = new ArrayList<>();
        Map<Long, String> productByAuth = new HashMap<>();

        List<TransactionResult> results = block.getTransactions();
        int index = 0;
        if (results != null) {
            for (TransactionResult result : results) {
                Object value = result == null ? null : result.get();
                if (!(value instanceof JsonTransactionResponse tx)) {
                    log.warn("区块 {} 存在仅返回哈希的交易，跳过明细", number);
                    index++;
                    continue;
                }
                TransactionReceipt receipt = fetchReceipt(client, tx.getHash());
                txRows.add(new TxRow(
                        lower(tx.getHash()),
                        number,
                        index,
                        lower(tx.getFrom()),
                        lower(tx.getTo()),
                        toDecimalString(tx.getValue()),
                        receipt == null ? null : gasOf(receipt),
                        receipt == null ? null : receipt.getStatus(),
                        tx.getInput()));

                if (receipt != null) {
                    parseReceiptEvents(number, timestamp, tx.getHash(), receipt, eventTopics,
                            logRows, tokenRows, authRows, productByAuth);
                }
                index++;
            }
        }

        int txCount = results == null ? 0 : results.size();
        List<TokenRow> finalTokenRows = dedupeTokens(tokenRows);

        transactionTemplate.executeWithoutResult(status -> {
            jdbc.update("REPLACE INTO eb_blocks(number, block_hash, parent_hash, `timestamp`, tx_count, gas_used, sealer) "
                            + "VALUES(?, ?, ?, ?, ?, ?, ?)",
                    number, lower(block.getHash()), parentHash, timestamp, txCount, gasUsed, sealer);
            for (TxRow tx : txRows) {
                jdbc.update("REPLACE INTO eb_txs(hash, block_number, tx_index, from_addr, to_addr, value, gas_used, status, input) "
                                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        tx.hash(), tx.blockNumber(), tx.index(), tx.from(), tx.to(),
                        tx.value(), tx.gasUsed(), tx.status(), tx.input());
            }
            for (LogRow logRow : logRows) {
                jdbc.update("INSERT INTO eb_logs(tx_hash, log_index, address, topic0, topics, data, block_number) "
                                + "VALUES(?, ?, ?, ?, ?, ?, ?)",
                        logRow.txHash(), logRow.index(), logRow.address(), logRow.topic0(),
                        logRow.topics(), logRow.data(), logRow.blockNumber());
            }
            for (TokenRow token : finalTokenRows) {
                // 使用 upsert 而非 REPLACE：避免重复索引时清空 token_uri / metadata_json 元数据缓存
                jdbc.update("INSERT INTO eb_tokens(token_id, contract, owner, mint_tx, block_number, block_time, product_hint) "
                                + "VALUES(?, ?, ?, ?, ?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE contract = VALUES(contract), owner = VALUES(owner), "
                                + "mint_tx = VALUES(mint_tx), block_number = VALUES(block_number), "
                                + "block_time = VALUES(block_time), product_hint = VALUES(product_hint)",
                        token.tokenId(), token.contract(), token.owner(), token.mintTx(),
                        token.blockNumber(), token.blockTime(), token.productHint());
            }
            for (AuthRow auth : authRows) {
                jdbc.update("REPLACE INTO eb_auths(auth_id, ip_owner, contract_no, auth_scope, max_mint, product_hint, block_number, tx_hash) "
                                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?)",
                        auth.authId(), auth.ipOwner(), auth.contractNo(), auth.scope(),
                        auth.maxMint(), auth.productHint(), auth.blockNumber(), auth.txHash());
            }
            jdbc.update("INSERT INTO eb_sync(k, v) VALUES(?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)",
                    SYNC_KEY, String.valueOf(number));
        });
        log.info("区块 {} 索引完成：交易 {}，日志 {}，铸造 {}，授权 {}",
                number, txRows.size(), logRows.size(), finalTokenRows.size(), authRows.size());
    }

    private TransactionReceipt fetchReceipt(Client client, String hash) {
        try {
            BcosTransactionReceipt response = client.getTransactionReceipt(hash, Boolean.FALSE);
            return response == null ? null : response.getTransactionReceipt();
        } catch (Exception e) {
            log.warn("获取交易回执失败 tx={}: {}", hash, e.getMessage());
            return null;
        }
    }

    /** 解析回执日志：落 eb_logs、识别 Transfer 铸造、识别授权登记 */
    private void parseReceiptEvents(long blockNumber, long blockTime, String txHash,
                                    TransactionReceipt receipt, String[] eventTopics,
                                    List<LogRow> logRows, List<TokenRow> tokenRows,
                                    List<AuthRow> authRows, Map<Long, String> productByAuth) {
        List<TransactionReceipt.Logs> logs = receipt.getLogEntries();
        if (logs == null || logs.isEmpty()) {
            return;
        }
        String transferTopic = eventTopics.length > 0 ? eventTopics[0] : "";
        String authTopic = eventTopics.length > 1 ? eventTopics[1] : "";
        String mintedTopic = eventTopics.length > 2 ? eventTopics[2] : "";

        // 先收集本交易里 NFTMinted 的 tokenId -> authId，便于给铸造记录关联产品
        Map<BigInteger, Long> mintedAuth = new HashMap<>();
        for (TransactionReceipt.Logs log : logs) {
            String topic0 = topic0(log);
            if (topic0 != null && topic0.equals(mintedTopic)) {
                List<String> topics = log.getTopics();
                if (topics != null && topics.size() >= 2 && topics.get(1) != null) {
                    BigInteger tokenId = decodeUint(topics.get(1));
                    BigInteger authId = decodeWord(log.getData(), 0);
                    if (tokenId != null && authId != null) {
                        mintedAuth.put(tokenId, authId.longValue());
                    }
                }
            }
        }

        int index = 0;
        for (TransactionReceipt.Logs log : logs) {
            List<String> topics = log.getTopics();
            String topic0 = topic0(log);
            String topicsJoined = topics == null ? null : String.join(",", topics);
            logRows.add(new LogRow(lower(txHash), index, lower(log.getAddress()),
                    topic0, topicsJoined, log.getData(), blockNumber));

            if (topic0 != null && topic0.equals(transferTopic)
                    && topics != null && topics.size() >= 4) {
                String from = decodeAddressTopic(topics.get(1));
                String to = decodeAddressTopic(topics.get(2));
                BigInteger tokenId = decodeUint(topics.get(3));
                if (ZERO_ADDRESS.equals(from) && tokenId != null) {
                    Long authId = mintedAuth.get(tokenId);
                    String product = authId == null ? "unknown" : productOfAuth(authId, productByAuth);
                    tokenRows.add(new TokenRow(tokenId.longValue(), lower(log.getAddress()), to,
                            lower(txHash), blockNumber, blockTime, product));
                }
            }

            if (topic0 != null && topic0.equals(authTopic)
                    && topics != null && topics.size() >= 3) {
                BigInteger authId = decodeUint(topics.get(1));
                String contractNo = decodeAuthContractNo(log.getData());
                BigInteger maxMint = decodeWord(log.getData(), 1);
                if (authId != null) {
                    String product = parseProductHint(contractNo);
                    productByAuth.put(authId.longValue(), product);
                    authRows.add(new AuthRow(authId.longValue(), decodeAddressTopic(topics.get(2)),
                            contractNo, -1, maxMint == null ? 0 : maxMint.intValue(),
                            product, blockNumber, lower(txHash)));
                }
            }
            index++;
        }
    }

    /** 同一 tokenId 在一个块内只保留最后一次记录 */
    private List<TokenRow> dedupeTokens(List<TokenRow> rows) {
        Map<Long, TokenRow> map = new java.util.LinkedHashMap<>();
        for (TokenRow row : rows) {
            map.put(row.tokenId(), row);
        }
        return new ArrayList<>(map.values());
    }

    /** 查询 token 对应授权的产品标识（先查本块已解析结果，再查库） */
    private String productOfAuth(long authId, Map<Long, String> known) {
        if (known.containsKey(authId)) {
            return known.get(authId);
        }
        try {
            List<String> values = jdbc.queryForList(
                    "SELECT product_hint FROM eb_auths WHERE auth_id = ?", String.class, authId);
            if (!values.isEmpty() && values.get(0) != null) {
                known.put(authId, values.get(0));
                return values.get(0);
            }
        } catch (Exception e) {
            log.debug("查询授权产品标识失败 authId={}: {}", authId, e.getMessage());
        }
        return "unknown";
    }

    private String resolveSealer(Block block) {
        List<String> sealers = block.getSealerList();
        int sealer = block.getSealer();
        if (sealers != null && sealer >= 0 && sealer < sealers.size()) {
            return sealers.get(sealer);
        }
        return String.valueOf(sealer);
    }

    /**
     * 由 contractNo 推导产品标识：第一个 '-' 之前的部分转小写；
     * 无 '-' 或为空时返回 unknown。
     * 例：OPENELF-UGC-xxx → openelf，LINKELF-SMOKE-xxx → linkelf。
     */
    public static String parseProductHint(String contractNo) {
        if (contractNo == null) {
            return "unknown";
        }
        String value = contractNo.trim();
        if (value.isEmpty()) {
            return "unknown";
        }
        int dash = value.indexOf('-');
        if (dash <= 0) {
            return "unknown";
        }
        String prefix = value.substring(0, dash).trim().toLowerCase(Locale.ROOT);
        return prefix.isEmpty() ? "unknown" : prefix;
    }

    /**
     * 解析 AuthorizationRegistered 事件 data 中的 contractNo（非 indexed 字符串）。
     * data 布局：offset(string) | maxMint | startTime | endTime | string。
     */
    public static String decodeAuthContractNo(String dataHex) {
        try {
            String data = strip0x(dataHex);
            if (data == null || data.length() < 64) {
                return null;
            }
            int offset = new BigInteger(data.substring(0, 64), 16).intValue();
            int charPos = offset * 2;
            if (charPos < 0 || charPos + 64 > data.length()) {
                return null;
            }
            int length = new BigInteger(data.substring(charPos, charPos + 64), 16).intValue();
            int start = charPos + 64;
            int end = start + length * 2;
            if (length < 0 || end > data.length()) {
                return null;
            }
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) {
                bytes[i] = (byte) Integer.parseInt(data.substring(start + i * 2, start + i * 2 + 2), 16);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 ABI data 中第 wordIndex 个 32 字节槽位为整数 */
    private static BigInteger decodeWord(String dataHex, int wordIndex) {
        try {
            String data = strip0x(dataHex);
            int start = wordIndex * 64;
            if (data == null || data.length() < start + 64) {
                return null;
            }
            return new BigInteger(data.substring(start, start + 64), 16);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 indexed uint256 topic */
    public static BigInteger decodeUint(String topicHex) {
        try {
            String topic = strip0x(topicHex);
            return topic == null || topic.isEmpty() ? null : new BigInteger(topic, 16);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 indexed address topic（取末 40 位） */
    public static String decodeAddressTopic(String topicHex) {
        String topic = strip0x(topicHex);
        if (topic == null || topic.length() < 40) {
            return null;
        }
        return "0x" + topic.substring(topic.length() - 40).toLowerCase(Locale.ROOT);
    }

    /** 金额/数值统一转为十进制字符串 */
    static String toDecimalString(String raw) {
        if (raw == null || raw.isBlank()) {
            return "0";
        }
        String value = raw.trim();
        if (value.startsWith("0x") || value.startsWith("0X")) {
            String hex = value.substring(2);
            if (hex.isEmpty()) {
                return "0";
            }
            try {
                return new BigInteger(hex, 16).toString();
            } catch (Exception e) {
                return "0";
            }
        }
        return value;
    }

    /** gas 等数值转 long：0x 前缀按十六进制，否则按十进制，解析失败返回 0 */
    static long parseHexLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        String value = raw.trim();
        if (value.startsWith("0x") || value.startsWith("0X")) {
            try {
                return new BigInteger(value.substring(2), 16).longValue();
            } catch (Exception e) {
                return 0L;
            }
        }
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            try {
                return new BigInteger(value, 16).longValue();
            } catch (Exception ignored) {
                return 0L;
            }
        }
    }

    private static Long gasOf(TransactionReceipt receipt) {
        return parseHexLong(receipt.getGasUsed());
    }

    private static String topic0(TransactionReceipt.Logs log) {
        List<String> topics = log.getTopics();
        if (topics == null || topics.isEmpty() || topics.get(0) == null) {
            return null;
        }
        return strip0x(topics.get(0)).toLowerCase(Locale.ROOT);
    }

    /** 按客户端加密类型（SM3/Keccak）计算事件签名 topic */
    private static String topicOf(Client client, String signature) {
        try {
            return strip0x(client.getCryptoSuite().hash(signature)).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            log.warn("计算事件 topic 失败 {}: {}", signature, e.getMessage());
            return "";
        }
    }

    private static String strip0x(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.startsWith("0x") || v.startsWith("0X") ? v.substring(2) : v;
    }

    private static String lower(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        // SDK 返回的地址可能不带 0x 前缀，统一补齐，避免前端显示与跳转不一致
        if (!normalized.startsWith("0x") && normalized.matches("^[0-9a-f]{40}$")) {
            return "0x" + normalized;
        }
        return normalized;
    }

    // ========== 内部数据载体 ==========

    private record TxRow(String hash, long blockNumber, int index, String from, String to,
                         String value, Long gasUsed, Integer status, String input) {
    }

    private record LogRow(String txHash, int index, String address, String topic0,
                          String topics, String data, long blockNumber) {
    }

    private record TokenRow(long tokenId, String contract, String owner, String mintTx,
                            long blockNumber, long blockTime, String productHint) {
    }

    private record AuthRow(long authId, String ipOwner, String contractNo, int scope, int maxMint,
                           String productHint, long blockNumber, String txHash) {
    }
}
