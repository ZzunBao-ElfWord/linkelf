package com.linksprite.explorer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 浏览器只读查询仓库（JdbcTemplate，不使用 JPA 实体）。
 * <p>
 * 分页约定：page 从 1 开始，size 默认 20、上限 100。
 * 所有方法返回前端可直接序列化为 JSON 的 Map / List。
 */
@Repository
@ConditionalOnProperty(prefix = "explorer", name = "enabled", havingValue = "true")
public class ExplorerRepository {

    /** 默认分页大小 */
    public static final int DEFAULT_SIZE = 20;
    /** 分页大小上限 */
    public static final int MAX_SIZE = 100;

    private final JdbcTemplate jdbc;

    public ExplorerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ========== 概览 ==========

    /** 索引库内的统计信息（链上实时数据由 Controller 合并） */
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("blockCount", count("SELECT COUNT(*) FROM eb_blocks"));
        data.put("txCount", count("SELECT COUNT(*) FROM eb_txs"));
        data.put("logCount", count("SELECT COUNT(*) FROM eb_logs"));
        data.put("tokenCount", count("SELECT COUNT(*) FROM eb_tokens"));
        data.put("authCount", count("SELECT COUNT(*) FROM eb_auths"));
        data.put("indexedBlock", lastIndexedBlock());
        return data;
    }

    /** 已索引到的最高区块；无数据返回 -1 */
    public long lastIndexedBlock() {
        Long value = jdbc.queryForObject("SELECT MAX(number) FROM eb_blocks", Long.class);
        return value == null ? -1L : value;
    }

    // ========== 区块 ==========

    /** 区块分页（按高度倒序） */
    public Map<String, Object> blocksPage(int page, int size) {
        int p = clampPage(page);
        int s = clampSize(size);
        long total = count("SELECT COUNT(*) FROM eb_blocks");
        List<Map<String, Object>> items = jdbc.query(
                "SELECT number, block_hash, parent_hash, `timestamp`, tx_count, gas_used, sealer "
                        + "FROM eb_blocks ORDER BY number DESC LIMIT ? OFFSET ?",
                BLOCK_MAPPER, s, offsetOf(p, s));
        return pageResult(p, s, total, items);
    }

    /** 区块详情（含交易列表）；不存在返回 null */
    public Map<String, Object> blockDetail(long number) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT number, block_hash, parent_hash, `timestamp`, tx_count, gas_used, sealer "
                        + "FROM eb_blocks WHERE number = ?",
                BLOCK_MAPPER, number);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> block = rows.get(0);
        block.put("transactions", jdbc.query(
                "SELECT hash, block_number, tx_index, from_addr, to_addr, value, gas_used, status, input "
                        + "FROM eb_txs WHERE block_number = ? ORDER BY tx_index ASC",
                TX_MAPPER, number));
        return block;
    }

    // ========== 交易 ==========

    /**
     * 交易分页；address 非空时过滤（发送方或接收方）。
     */
    public Map<String, Object> txsPage(int page, int size, String address) {
        int p = clampPage(page);
        int s = clampSize(size);
        String addr = normalize(address);
        if (addr == null) {
            long total = count("SELECT COUNT(*) FROM eb_txs");
            List<Map<String, Object>> items = jdbc.query(
                    "SELECT hash, block_number, tx_index, from_addr, to_addr, value, gas_used, status, input "
                            + "FROM eb_txs ORDER BY block_number DESC, tx_index DESC LIMIT ? OFFSET ?",
                    TX_MAPPER, s, offsetOf(p, s));
            return pageResult(p, s, total, items);
        }
        long total = count("SELECT COUNT(*) FROM eb_txs WHERE from_addr = ? OR to_addr = ?", addr, addr);
        List<Map<String, Object>> items = jdbc.query(
                "SELECT hash, block_number, tx_index, from_addr, to_addr, value, gas_used, status, input "
                        + "FROM eb_txs WHERE from_addr = ? OR to_addr = ? "
                        + "ORDER BY block_number DESC, tx_index DESC LIMIT ? OFFSET ?",
                TX_MAPPER, addr, addr, s, offsetOf(p, s));
        return pageResult(p, s, total, items);
    }

    /** 交易详情（含日志）；不存在返回 null */
    public Map<String, Object> txDetail(String hash) {
        String h = normalize(hash);
        if (h == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT hash, block_number, tx_index, from_addr, to_addr, value, gas_used, status, input "
                        + "FROM eb_txs WHERE hash = ?",
                TX_MAPPER, h);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> tx = rows.get(0);
        tx.put("logs", jdbc.query(
                "SELECT tx_hash, log_index, address, topic0, topics, data, block_number "
                        + "FROM eb_logs WHERE tx_hash = ? ORDER BY log_index ASC",
                LOG_MAPPER, h));
        return tx;
    }

    // ========== 地址 ==========

    /** 地址详情：交易计数、持有 NFT、最近交易（余额由 Controller 链上实时查询） */
    public Map<String, Object> addressDetail(String address) {
        String addr = normalize(address);
        if (addr == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("address", addr);
        data.put("txCount", count("SELECT COUNT(*) FROM eb_txs WHERE from_addr = ? OR to_addr = ?", addr, addr));
        data.put("sentCount", count("SELECT COUNT(*) FROM eb_txs WHERE from_addr = ?", addr));
        data.put("receivedCount", count("SELECT COUNT(*) FROM eb_txs WHERE to_addr = ?", addr));
        data.put("tokenCount", count("SELECT COUNT(*) FROM eb_tokens WHERE owner = ?", addr));
        data.put("tokens", jdbc.query(
                "SELECT token_id, contract, owner, mint_tx, block_number, block_time, product_hint "
                        + "FROM eb_tokens WHERE owner = ? ORDER BY token_id DESC LIMIT 100",
                TOKEN_MAPPER, addr));
        data.put("transactions", txsPage(1, DEFAULT_SIZE, addr).get("items"));
        return data;
    }

    // ========== 合约 ==========

    /**
     * 合约详情：交易数 / 日志数 / 最近活跃块 + 最近 20 笔交易 + 最近 20 条事件。
     * 仅使用已有索引（eb_txs.idx_tx_to、eb_logs.idx_log_addr），并限制返回行数。
     */
    public Map<String, Object> contractDetail(String address) {
        Map<String, Object> data = contractStats(address);
        if (data == null) {
            return null;
        }
        String addr = (String) data.get("address");
        data.put("transactions", contractTxs(addr, DEFAULT_SIZE));
        data.put("events", contractLogs(addr, DEFAULT_SIZE));
        return data;
    }

    /** 合约聚合统计：交易数 / 日志数 / 最近活跃区块（列表页使用，避免拉取明细） */
    public Map<String, Object> contractStats(String address) {
        String addr = normalize(address);
        if (addr == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("address", addr);
        data.put("txCount", count("SELECT COUNT(*) FROM eb_txs WHERE to_addr = ?", addr));
        data.put("logCount", count("SELECT COUNT(*) FROM eb_logs WHERE address = ?", addr));

        Long lastTxBlock = jdbc.queryForObject(
                "SELECT MAX(block_number) FROM eb_txs WHERE to_addr = ?", Long.class, addr);
        Long lastLogBlock = jdbc.queryForObject(
                "SELECT MAX(block_number) FROM eb_logs WHERE address = ?", Long.class, addr);
        Long lastActive = null;
        if (lastTxBlock != null || lastLogBlock != null) {
            lastActive = Math.max(lastTxBlock == null ? -1L : lastTxBlock, lastLogBlock == null ? -1L : lastLogBlock);
            if (lastActive < 0) {
                lastActive = null;
            }
        }
        data.put("lastActiveBlock", lastActive);
        Long lastActiveTime = null;
        if (lastActive != null) {
            List<Long> times = jdbc.queryForList(
                    "SELECT `timestamp` FROM eb_blocks WHERE number = ?", Long.class, lastActive);
            lastActiveTime = times.isEmpty() ? null : times.get(0);
        }
        data.put("lastActiveTime", lastActiveTime);
        return data;
    }

    /** 合约最近交易（按 to_addr 过滤，最多 limit 条） */
    public List<Map<String, Object>> contractTxs(String address, int limit) {
        int n = Math.max(1, Math.min(limit, MAX_SIZE));
        return jdbc.query(
                "SELECT hash, block_number, tx_index, from_addr, to_addr, value, gas_used, status, input "
                        + "FROM eb_txs WHERE to_addr = ? ORDER BY block_number DESC, tx_index DESC LIMIT ?",
                TX_MAPPER, normalize(address), n);
    }

    /** 合约最近事件（按 address 过滤，最多 limit 条） */
    public List<Map<String, Object>> contractLogs(String address, int limit) {
        int n = Math.max(1, Math.min(limit, MAX_SIZE));
        return jdbc.query(
                "SELECT tx_hash, log_index, address, topic0, topics, data, block_number "
                        + "FROM eb_logs WHERE address = ? ORDER BY block_number DESC, log_index DESC, id DESC LIMIT ?",
                LOG_MAPPER, normalize(address), n);
    }

    // ========== 事件日志检索 ==========

    /**
     * 事件日志分页。
     *
     * @param address 合约地址（可选）
     * @param topics  事件 topic0 列表（可选，SM3/Keccak 同时匹配）
     * @param tx      交易哈希（可选）
     * @param block   区块高度（可选）
     */
    public Map<String, Object> logsPage(int page, int size, String address,
                                        List<String> topics, String tx, Long block) {
        int p = clampPage(page);
        int s = clampSize(size);
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        String addr = normalize(address);
        if (addr != null) {
            where.append(" AND address = ?");
            args.add(addr);
        }
        if (topics != null && !topics.isEmpty()) {
            where.append(" AND topic0 IN (");
            for (int i = 0; i < topics.size(); i++) {
                if (i > 0) {
                    where.append(',');
                }
                where.append('?');
                args.add(topics.get(i));
            }
            where.append(')');
        }
        String txHash = normalize(tx);
        if (txHash != null) {
            where.append(" AND tx_hash = ?");
            args.add(txHash);
        }
        if (block != null) {
            where.append(" AND block_number = ?");
            args.add(block);
        }
        String condition = where.length() == 0 ? "" : " WHERE " + where.substring(5);
        long total = count("SELECT COUNT(*) FROM eb_logs" + condition, args.toArray());
        args.add(s);
        args.add(offsetOf(p, s));
        List<Map<String, Object>> items = jdbc.query(
                "SELECT tx_hash, log_index, address, topic0, topics, data, block_number FROM eb_logs"
                        + condition + " ORDER BY block_number DESC, log_index DESC, id DESC LIMIT ? OFFSET ?",
                LOG_MAPPER, args.toArray());
        return pageResult(p, s, total, items);
    }

    // ========== Token 详情 ==========

    /** Token 详情（含 tokenURI / 元数据缓存）；不存在返回 null */
    public Map<String, Object> tokenDetail(long tokenId) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT token_id, contract, owner, mint_tx, block_number, block_time, product_hint, "
                        + "token_uri, metadata_json FROM eb_tokens WHERE token_id = ?",
                TOKEN_DETAIL_MAPPER, tokenId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Token 是否存在 */
    public boolean tokenExists(long tokenId) {
        return count("SELECT COUNT(*) FROM eb_tokens WHERE token_id = ?", tokenId) > 0;
    }

    /** 回写 tokenURI 与元数据缓存（失败由调用方忽略） */
    public void updateTokenMetadata(long tokenId, String tokenUri, String metadataJson) {
        jdbc.update("UPDATE eb_tokens SET token_uri = ?, metadata_json = ? WHERE token_id = ?",
                tokenUri, metadataJson, tokenId);
    }

    /**
     * Token 转移历史：NFT 合约发出的 Transfer 事件，topics 中匹配 tokenId。
     * 最多返回 100 条，附带区块时间。
     */
    public List<Map<String, Object>> tokenTransfers(String contract, List<String> transferTopics,
                                                    long tokenId, int limit) {
        if (transferTopics == null || transferTopics.isEmpty()) {
            return List.of();
        }
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        String addr = normalize(contract);
        if (addr != null) {
            where.append(" AND l.address = ?");
            args.add(addr);
        }
        where.append(" AND l.topic0 IN (");
        for (int i = 0; i < transferTopics.size(); i++) {
            if (i > 0) {
                where.append(',');
            }
            where.append('?');
            args.add(transferTopics.get(i));
        }
        where.append(')');
        // tokenId 填充为 32 字节十六进制；兼容带/不带 0x 前缀的 topics 存储
        String word = String.format("%064x", new java.math.BigInteger(Long.toUnsignedString(tokenId)));
        where.append(" AND l.topics LIKE ?");
        args.add("%" + word + "%");
        int n = Math.max(1, Math.min(limit, MAX_SIZE));
        args.add(n);
        return jdbc.query(
                "SELECT l.tx_hash, l.log_index, l.address, l.topic0, l.topics, l.data, l.block_number, "
                        + "b.`timestamp` AS block_time FROM eb_logs l "
                        + "LEFT JOIN eb_blocks b ON b.number = l.block_number"
                        + " WHERE " + where.substring(5)
                        + " ORDER BY l.block_number DESC, l.log_index DESC LIMIT ?",
                LOG_TIME_MAPPER, args.toArray());
    }

    // ========== Token / 授权 ==========

    /** Token 分页；product 非空且非 all 时按 product_hint 过滤 */
    public Map<String, Object> tokensPage(int page, int size, String product) {
        int p = clampPage(page);
        int s = clampSize(size);
        String hint = normalizeProduct(product);
        if (hint == null) {
            long total = count("SELECT COUNT(*) FROM eb_tokens");
            List<Map<String, Object>> items = jdbc.query(
                    "SELECT token_id, contract, owner, mint_tx, block_number, block_time, product_hint "
                            + "FROM eb_tokens ORDER BY token_id DESC LIMIT ? OFFSET ?",
                    TOKEN_MAPPER, s, offsetOf(p, s));
            return pageResult(p, s, total, items);
        }
        long total = count("SELECT COUNT(*) FROM eb_tokens WHERE product_hint = ?", hint);
        List<Map<String, Object>> items = jdbc.query(
                "SELECT token_id, contract, owner, mint_tx, block_number, block_time, product_hint "
                        + "FROM eb_tokens WHERE product_hint = ? ORDER BY token_id DESC LIMIT ? OFFSET ?",
                TOKEN_MAPPER, hint, s, offsetOf(p, s));
        return pageResult(p, s, total, items);
    }

    /** 授权分页；product 非空且非 all 时按 product_hint 过滤 */
    public Map<String, Object> authsPage(int page, int size, String product) {
        int p = clampPage(page);
        int s = clampSize(size);
        String hint = normalizeProduct(product);
        if (hint == null) {
            long total = count("SELECT COUNT(*) FROM eb_auths");
            List<Map<String, Object>> items = jdbc.query(
                    "SELECT a.auth_id, a.ip_owner, a.contract_no, a.auth_scope, a.max_mint, a.product_hint, "
                            + "a.block_number, a.tx_hash, b.`timestamp` AS block_time "
                            + "FROM eb_auths a LEFT JOIN eb_blocks b ON b.number = a.block_number "
                            + "ORDER BY a.auth_id DESC LIMIT ? OFFSET ?",
                    AUTH_MAPPER, s, offsetOf(p, s));
            return pageResult(p, s, total, items);
        }
        long total = count("SELECT COUNT(*) FROM eb_auths WHERE product_hint = ?", hint);
        List<Map<String, Object>> items = jdbc.query(
                "SELECT a.auth_id, a.ip_owner, a.contract_no, a.auth_scope, a.max_mint, a.product_hint, "
                        + "a.block_number, a.tx_hash, b.`timestamp` AS block_time "
                        + "FROM eb_auths a LEFT JOIN eb_blocks b ON b.number = a.block_number "
                        + "WHERE a.product_hint = ? ORDER BY a.auth_id DESC LIMIT ? OFFSET ?",
                AUTH_MAPPER, hint, s, offsetOf(p, s));
        return pageResult(p, s, total, items);
    }

    // ========== 搜索 ==========

    /**
     * 搜索：交易哈希（0x + 64 位）、地址（0x + 40 位）、
     * tokenId（"12" 或 "#12"，纯数字优先按 Token 解析，未命中再按块高）。
     * 未命中返回 null（Controller 转 404）。
     */
    public Map<String, Object> search(String q) {
        String type = detectSearchType(q);
        Long tokenId = tokenIdOf(q);
        if ("unknown".equals(type) && tokenId == null) {
            return null;
        }
        String value = q.trim();
        Map<String, Object> data = new LinkedHashMap<>();
        // 纯数字优先按 tokenId 解析（P2 搜索约定：12 与 #12 均指向 Token），未命中再按块高
        if (tokenId != null && tokenExists(tokenId)) {
            return tokenResult(tokenId);
        }
        switch (type) {
            case "block" -> {
                long number;
                try {
                    number = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (count("SELECT COUNT(*) FROM eb_blocks WHERE number = ?", number) > 0) {
                    data.put("type", "block");
                    data.put("number", number);
                    return data;
                }
                return null;
            }
            case "tx" -> {
                String hash = value.toLowerCase(Locale.ROOT);
                if (count("SELECT COUNT(*) FROM eb_txs WHERE hash = ?", hash) == 0) {
                    return null;
                }
                data.put("type", "tx");
                data.put("hash", hash);
            }
            case "address" -> {
                data.put("type", "address");
                data.put("address", value.toLowerCase(Locale.ROOT));
            }
            default -> {
                return tokenId != null && tokenExists(tokenId) ? tokenResult(tokenId) : null;
            }
        }
        return data;
    }

    private Map<String, Object> tokenResult(long tokenId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "token");
        data.put("tokenId", tokenId);
        return data;
    }

    /**
     * 判定输入是否为 tokenId：支持 "12" 与 "#12"。
     * 非法或超出 long 范围返回 null。
     */
    public static Long tokenIdOf(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String value = q.trim();
        if (value.startsWith("#")) {
            value = value.substring(1).trim();
        }
        if (!value.matches("[0-9]+")) {
            return null;
        }
        try {
            long id = Long.parseLong(value);
            return id < 0 ? null : id;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判定搜索输入类型：block / tx / address / unknown。
     */
    public static String detectSearchType(String q) {
        if (q == null || q.isBlank()) {
            return "unknown";
        }
        String value = q.trim();
        if (value.matches("[0-9]+")) {
            return "block";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.matches("0x[0-9a-f]{64}")) {
            return "tx";
        }
        if (lower.matches("0x[0-9a-f]{40}")) {
            return "address";
        }
        return "unknown";
    }

    // ========== 分页/格式化工具（单元测试覆盖） ==========

    /**
     * 分页参数校验：page ≥ 1、1 ≤ size ≤ 100。
     * 返回中文错误信息；合法返回 null（Controller 转 400）。
     */
    public static String validatePage(int page, int size) {
        if (page < 1) {
            return "page 参数必须大于等于 1";
        }
        if (size < 1) {
            return "size 参数必须大于等于 1";
        }
        if (size > MAX_SIZE) {
            return "size 参数不得超过 100";
        }
        return null;
    }

    /** page 小于 1 时归一到 1 */
    public static int clampPage(int page) {
        return page < 1 ? 1 : page;
    }

    /** size 小于 1 时取默认 20，大于 100 时钳制到 100 */
    public static int clampSize(int size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** 计算 SQL OFFSET */
    public static long offsetOf(int page, int size) {
        return (long) (clampPage(page) - 1) * clampSize(size);
    }

    /** 方法选择器：input 前 10 个字符（0x + 8 位） */
    static String methodOf(String input) {
        if (input == null || !input.startsWith("0x") || input.length() < 10) {
            return null;
        }
        return input.substring(0, 10);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeProduct(String product) {
        if (product == null || product.isBlank() || "all".equalsIgnoreCase(product.trim())) {
            return null;
        }
        return product.trim().toLowerCase(Locale.ROOT);
    }

    private long count(String sql, Object... args) {
        Number value = jdbc.queryForObject(sql, Number.class, args);
        return value == null ? 0L : value.longValue();
    }

    private Map<String, Object> pageResult(int page, int size, long total, List<Map<String, Object>> items) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page);
        data.put("size", size);
        data.put("total", total);
        data.put("totalPages", total == 0 ? 0 : (int) ((total + size - 1) / size));
        data.put("items", items);
        return data;
    }

    private static Long getLongOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    // ========== RowMapper ==========

    private static final RowMapper<Map<String, Object>> BLOCK_MAPPER = (rs, rowNum) -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("number", rs.getLong("number"));
        m.put("blockHash", rs.getString("block_hash"));
        m.put("parentHash", rs.getString("parent_hash"));
        m.put("timestamp", rs.getLong("timestamp"));
        m.put("txCount", rs.getInt("tx_count"));
        m.put("gasUsed", getLongOrNull(rs, "gas_used"));
        m.put("sealer", rs.getString("sealer"));
        return m;
    };

    private static final RowMapper<Map<String, Object>> TX_MAPPER = (rs, rowNum) -> {
        Map<String, Object> m = new LinkedHashMap<>();
        String input = rs.getString("input");
        m.put("hash", rs.getString("hash"));
        m.put("blockNumber", rs.getLong("block_number"));
        m.put("txIndex", rs.getInt("tx_index"));
        m.put("from", rs.getString("from_addr"));
        m.put("to", rs.getString("to_addr"));
        m.put("value", rs.getString("value"));
        m.put("gasUsed", getLongOrNull(rs, "gas_used"));
        m.put("status", getLongOrNull(rs, "status"));
        m.put("method", methodOf(input));
        m.put("input", input);
        return m;
    };

    private static final RowMapper<Map<String, Object>> LOG_MAPPER = (rs, rowNum) -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("txHash", rs.getString("tx_hash"));
        m.put("logIndex", rs.getInt("log_index"));
        m.put("address", rs.getString("address"));
        m.put("topic0", rs.getString("topic0"));
        m.put("topics", rs.getString("topics"));
        m.put("data", rs.getString("data"));
        m.put("blockNumber", rs.getLong("block_number"));
        return m;
    };

    private static final RowMapper<Map<String, Object>> LOG_TIME_MAPPER = (rs, rowNum) -> {
        Map<String, Object> m = LOG_MAPPER.mapRow(rs, rowNum);
        if (m != null) {
            m.put("blockTime", getLongOrNull(rs, "block_time"));
        }
        return m;
    };

    private static final RowMapper<Map<String, Object>> TOKEN_MAPPER = (rs, rowNum) -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tokenId", rs.getLong("token_id"));
        m.put("contract", rs.getString("contract"));
        m.put("owner", rs.getString("owner"));
        m.put("mintTx", rs.getString("mint_tx"));
        m.put("blockNumber", rs.getLong("block_number"));
        m.put("blockTime", getLongOrNull(rs, "block_time"));
        m.put("productHint", rs.getString("product_hint"));
        return m;
    };

    private static final RowMapper<Map<String, Object>> TOKEN_DETAIL_MAPPER = (rs, rowNum) -> {
        Map<String, Object> m = new LinkedHashMap<>(TOKEN_MAPPER.mapRow(rs, rowNum));
        m.put("tokenUri", rs.getString("token_uri"));
        m.put("metadataJson", rs.getString("metadata_json"));
        return m;
    };

    private static final RowMapper<Map<String, Object>> AUTH_MAPPER = (rs, rowNum) -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("authId", rs.getLong("auth_id"));
        m.put("ipOwner", rs.getString("ip_owner"));
        m.put("contractNo", rs.getString("contract_no"));
        m.put("authScope", getLongOrNull(rs, "auth_scope"));
        m.put("maxMint", getLongOrNull(rs, "max_mint"));
        m.put("productHint", rs.getString("product_hint"));
        m.put("blockNumber", rs.getLong("block_number"));
        m.put("blockTime", getLongOrNull(rs, "block_time"));
        m.put("txHash", rs.getString("tx_hash"));
        return m;
    };
}
