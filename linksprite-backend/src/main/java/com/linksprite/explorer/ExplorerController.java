package com.linksprite.explorer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linksprite.common.Result;
import com.linksprite.config.ContractConfig;
import com.linksprite.service.ChainStatusService;
import com.linksprite.service.NftService;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.client.protocol.response.Code;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 轻量区块链浏览器只读 API。
 * <p>
 * 对外路径：/api/explorer/**（context-path=/api），由 ApiKeyFilter 白名单放行，无需 API-Key。
 */
@Slf4j
@RestController
@RequestMapping("/explorer")
@ConditionalOnProperty(prefix = "explorer", name = "enabled", havingValue = "true")
public class ExplorerController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 外链元数据抓取客户端：不跟随重定向，避免绕过内网地址校验 */
    private static final HttpClient METADATA_HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    /** tokenURI 链上查询超时（秒） */
    private static final long TOKEN_URI_TIMEOUT_SECONDS = 3L;
    private static final String ADDRESS_PATTERN = "0x[0-9a-fA-F]{40}";
    private static final String TX_PATTERN = "0x[0-9a-fA-F]{64}";

    private final ExplorerRepository repository;
    private final ExplorerProperties properties;
    private final ChainStatusService chainStatus;
    private final ObjectProvider<Client> clientProvider;
    private final ObjectProvider<NftService> nftServiceProvider;
    private final ExplorerEventAbi eventAbi;
    private final ContractConfig contractConfig;
    private final ExecutorService chainExecutor;

    public ExplorerController(ExplorerRepository repository,
                              ExplorerProperties properties,
                              ChainStatusService chainStatus,
                              ObjectProvider<Client> clientProvider,
                              ObjectProvider<NftService> nftServiceProvider,
                              ExplorerEventAbi eventAbi,
                              ContractConfig contractConfig,
                              @Qualifier("explorerChainExecutor") ExecutorService chainExecutor) {
        this.repository = repository;
        this.properties = properties;
        this.chainStatus = chainStatus;
        this.clientProvider = clientProvider;
        this.nftServiceProvider = nftServiceProvider;
        this.eventAbi = eventAbi;
        this.contractConfig = contractConfig;
        this.chainExecutor = chainExecutor;
    }

    /** 概览：链信息 + 索引统计 + 索引滞后 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> stats = repository.overview();
        Client client = clientProvider.getIfAvailable();

        Long latestBlock = null;
        Long totalTx = null;
        String groupId = properties.getGroupId();
        if (client != null) {
            groupId = client.getGroup();
            try {
                latestBlock = client.getBlockNumber().getBlockNumber().longValue();
            } catch (Exception e) {
                log.warn("查询最新块高失败: {}", e.getMessage());
            }
            try {
                BigInteger count = new BigInteger(
                        client.getTotalTransactionCount().getTotalTransactionCount().getTransactionCount());
                totalTx = count.longValue();
            } catch (Exception e) {
                log.warn("查询总交易数失败: {}", e.getMessage());
            }
        }

        long indexedBlock = ((Number) stats.getOrDefault("indexedBlock", -1L)).longValue();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chainId", chainStatus.getChainId());
        data.put("groupId", groupId);
        data.put("chainConnected", client != null);
        data.put("latestBlock", latestBlock);
        data.put("totalTx", totalTx);
        data.put("indexedBlock", indexedBlock);
        data.put("lag", latestBlock == null ? null : Math.max(0L, latestBlock - indexedBlock));
        data.put("blockCount", stats.get("blockCount"));
        data.put("txCount", stats.get("txCount"));
        data.put("logCount", stats.get("logCount"));
        data.put("tokenCount", stats.get("tokenCount"));
        data.put("authCount", stats.get("authCount"));
        return Result.success(data);
    }

    /** 区块分页 */
    @GetMapping("/blocks")
    public ResponseEntity<Result<Map<String, Object>>> blocks(@RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        String error = ExplorerRepository.validatePage(page, size);
        if (error != null) {
            return badRequest(error);
        }
        return ResponseEntity.ok(Result.success(repository.blocksPage(page, size)));
    }

    /** 区块详情（含交易列表） */
    @GetMapping("/blocks/{number}")
    public ResponseEntity<Result<Map<String, Object>>> block(@PathVariable long number) {
        Map<String, Object> data = repository.blockDetail(number);
        if (data == null) {
            return notFound("区块不存在: " + number);
        }
        return ResponseEntity.ok(Result.success(data));
    }

    /** 交易分页（可按地址过滤） */
    @GetMapping("/txs")
    public ResponseEntity<Result<Map<String, Object>>> txs(@RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) String address) {
        String error = ExplorerRepository.validatePage(page, size);
        if (error != null) {
            return badRequest(error);
        }
        if (address != null && !address.isBlank() && !address.trim().matches(ADDRESS_PATTERN)) {
            return badRequest("address 参数格式不正确");
        }
        return ResponseEntity.ok(Result.success(repository.txsPage(page, size, address)));
    }

    /** 交易详情（含事件日志） */
    @GetMapping("/txs/{hash}")
    public ResponseEntity<Result<Map<String, Object>>> tx(@PathVariable String hash) {
        Map<String, Object> data = repository.txDetail(hash);
        if (data == null) {
            return notFound("交易不存在: " + hash);
        }
        return ResponseEntity.ok(Result.success(data));
    }

    /** 地址详情：链上实时余额 + 交易 + 持有 NFT */
    @GetMapping("/address/{addr}")
    public ResponseEntity<Result<Map<String, Object>>> address(@PathVariable String addr) {
        if (!addr.matches(ADDRESS_PATTERN)) {
            return notFound("地址格式不正确: " + addr);
        }
        Map<String, Object> data = repository.addressDetail(addr);
        BigInteger balance = null;
        try {
            NftService nftService = nftServiceProvider.getIfAvailable();
            if (nftService != null) {
                balance = nftService.balanceOf(addr);
            }
        } catch (Exception e) {
            log.warn("链上实时查询 NFT 余额失败 addr={}: {}", addr, e.getMessage());
        }
        data.put("balance", balance);
        data.put("balanceSource", "chain");
        return ResponseEntity.ok(Result.success(data));
    }

    /** Token 分页（product=openelf/linkelf/koko/all） */
    @GetMapping("/tokens")
    public ResponseEntity<Result<Map<String, Object>>> tokens(@RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size,
                                                              @RequestParam(required = false) String product) {
        String error = ExplorerRepository.validatePage(page, size);
        if (error != null) {
            return badRequest(error);
        }
        return ResponseEntity.ok(Result.success(repository.tokensPage(page, size, product)));
    }

    /** Token 详情：基础信息 + tokenURI/元数据缓存 + 转移历史 */
    @GetMapping("/tokens/{tokenId}")
    public ResponseEntity<Result<Map<String, Object>>> token(@PathVariable String tokenId) {
        Long id = ExplorerRepository.tokenIdOf(tokenId);
        if (id == null) {
            return badRequest("tokenId 必须为非负整数");
        }
        Map<String, Object> data = repository.tokenDetail(id);
        if (data == null) {
            return notFound("Token 不存在: " + tokenId);
        }
        String cachedUri = (String) data.get("tokenUri");
        String cachedMetadata = (String) data.remove("metadataJson");
        if (cachedUri == null || cachedUri.isBlank() || cachedMetadata == null) {
            String uri = queryTokenUri(id);
            if (uri != null && !uri.isBlank()) {
                String metadataJson = extractMetadataJson(uri);
                if (metadataJson == null) {
                    // 外链元数据（如 https://api.openelfai.com/...）：服务端抓取一次并缓存
                    metadataJson = fetchRemoteMetadata(uri);
                }
                try {
                    repository.updateTokenMetadata(id, uri, metadataJson);
                } catch (Exception e) {
                    log.warn("缓存 tokenURI 失败 tokenId={}: {}", id, e.getMessage());
                }
                data.put("tokenUri", uri);
                cachedMetadata = metadataJson;
            } else if (cachedUri == null || cachedUri.isBlank()) {
                data.put("tokenUri", null);
            }
        }
        data.put("metadata", parseMetadata(cachedMetadata));

        List<String> transferTopics = eventAbi.topicsOf("Transfer");
        data.put("transfers", enrichLogs(repository.tokenTransfers(
                (String) data.get("contract"), transferTopics, id, 100)));
        return ResponseEntity.ok(Result.success(data));
    }

    /** 授权分页（product=openelf/linkelf/koko/all） */
    @GetMapping("/auths")
    public ResponseEntity<Result<Map<String, Object>>> auths(@RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int size,
                                                             @RequestParam(required = false) String product) {
        String error = ExplorerRepository.validatePage(page, size);
        if (error != null) {
            return badRequest(error);
        }
        return ResponseEntity.ok(Result.success(repository.authsPage(page, size, product)));
    }

    /** 合约列表：配置中的合约地址 + 索引聚合 + 链上 getCode 部署状态 */
    @GetMapping("/contracts")
    public Result<List<Map<String, Object>>> contracts() {
        Client client = clientProvider.getIfAvailable();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> meta : contractMetas()) {
            String address = (String) meta.get("address");
            Map<String, Object> item = new LinkedHashMap<>(meta);
            Map<String, Object> stats = repository.contractStats(address);
            if (stats != null) {
                item.put("txCount", stats.get("txCount"));
                item.put("logCount", stats.get("logCount"));
                item.put("lastActiveBlock", stats.get("lastActiveBlock"));
                item.put("lastActiveTime", stats.get("lastActiveTime"));
            }
            item.put("deployed", isDeployed(client, address));
            items.add(item);
        }
        return Result.success(items);
    }

    /** 合约详情：基础信息 + 最近 20 笔交易 + 最近 20 条事件 */
    @GetMapping("/contracts/{address}")
    public ResponseEntity<Result<Map<String, Object>>> contract(@PathVariable String address) {
        if (!address.matches(ADDRESS_PATTERN)) {
            return badRequest("合约地址格式不正确");
        }
        Map<String, Object> data = repository.contractDetail(address);
        if (data == null) {
            return notFound("合约地址格式不正确: " + address);
        }
        Map<String, Object> meta = contractMeta(address);
        data.put("key", meta == null ? null : meta.get("key"));
        data.put("name", meta == null ? "未登记合约" : meta.get("name"));
        data.put("configured", meta != null);
        data.put("deployed", isDeployed(clientProvider.getIfAvailable(), address));
        data.put("events", enrichLogs((List<Map<String, Object>>) data.get("events")));
        return ResponseEntity.ok(Result.success(data));
    }

    /** 事件日志分页（address / event / tx / block 过滤） */
    @GetMapping("/logs")
    public ResponseEntity<Result<Map<String, Object>>> logs(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size,
                                                            @RequestParam(required = false) String address,
                                                            @RequestParam(required = false) String event,
                                                            @RequestParam(required = false) String tx,
                                                            @RequestParam(required = false) String block) {
        String error = ExplorerRepository.validatePage(page, size);
        if (error != null) {
            return badRequest(error);
        }
        if (address != null && !address.isBlank() && !address.trim().matches(ADDRESS_PATTERN)) {
            return badRequest("address 参数格式不正确");
        }
        if (tx != null && !tx.isBlank() && !tx.trim().matches(TX_PATTERN)) {
            return badRequest("tx 参数格式不正确");
        }
        List<String> topics = null;
        if (event != null && !event.isBlank()) {
            if (!eventAbi.hasEvent(event)) {
                return badRequest("未知事件名: " + event);
            }
            topics = eventAbi.topicsOf(event);
        }
        Long blockNumber = null;
        if (block != null && !block.isBlank()) {
            try {
                blockNumber = Long.parseLong(block.trim());
            } catch (NumberFormatException e) {
                return badRequest("block 参数必须为整数");
            }
            if (blockNumber < 0) {
                return badRequest("block 参数必须为非负整数");
            }
        }
        Map<String, Object> data = repository.logsPage(page, size, address, topics, tx, blockNumber);
        data.put("items", enrichLogs((List<Map<String, Object>>) data.get("items")));
        return ResponseEntity.ok(Result.success(data));
    }

    /** 已登记事件名列表（供前端下拉） */
    @GetMapping("/events")
    public Result<List<String>> events() {
        return Result.success(eventAbi.eventNames());
    }

    /** 搜索：块高 / 交易哈希 / 地址 / 合约地址 / tokenId */
    @GetMapping("/search")
    public ResponseEntity<Result<Map<String, Object>>> search(@RequestParam String q) {
        Map<String, Object> data = repository.search(q);
        if (data == null) {
            return notFound("未找到匹配结果: " + q);
        }
        Object type = data.get("type");
        if ("address".equals(type)) {
            String addr = String.valueOf(data.get("address"));
            if (contractMeta(addr) != null) {
                data.put("type", "contract");
            }
        }
        return ResponseEntity.ok(Result.success(data));
    }

    /** 浏览器健康状态 */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", properties.isEnabled());
        try {
            long indexed = repository.lastIndexedBlock();
            long latest = -1L;
            Client client = clientProvider.getIfAvailable();
            if (client != null) {
                latest = client.getBlockNumber().getBlockNumber().longValue();
            }
            data.put("status", "UP");
            data.put("chainConnected", client != null);
            data.put("latestBlock", latest < 0 ? null : latest);
            data.put("indexedBlock", indexed);
            data.put("lag", latest < 0 ? null : Math.max(0L, latest - indexed));
        } catch (Exception e) {
            log.warn("浏览器健康检查失败: {}", e.getMessage());
            data.put("status", "DOWN");
            data.put("error", e.getMessage());
        }
        return Result.success(data);
    }

    // ========== 合约配置 ==========

    /** 配置中的合约清单（名称用于前端展示） */
    private List<Map<String, Object>> contractMetas() {
        List<Map<String, Object>> list = new ArrayList<>();
        addContract(list, "authorizationRegistry", "AuthorizationRegistry", "授权登记", contractConfig.getAuthorizationRegistry());
        addContract(list, "linkSpriteNft", "LinkSpriteNFT", "NFT 合约", contractConfig.getLinkspriteNft());
        addContract(list, "transferPolicy", "TransferPolicy", "流转策略", contractConfig.getTransferPolicy());
        addContract(list, "kycRegistry", "KYCRegistry", "实名认证", contractConfig.getKycRegistry());
        addContract(list, "spriteComputeToken", "SpriteComputeToken", "算力词元", contractConfig.getSpriteComputeToken());
        addContract(list, "aiAccountFactory", "AIAccountFactory", "AI 账户工厂", contractConfig.getAiAccountFactory());
        addContract(list, "paymaster", "Paymaster", "Gas 代付", contractConfig.getPaymaster());
        return list;
    }

    private void addContract(List<Map<String, Object>> list, String key, String contract, String name, String address) {
        if (address == null || address.isBlank()) {
            return;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("key", key);
        meta.put("contract", contract);
        meta.put("name", name);
        meta.put("address", address.trim().toLowerCase(Locale.ROOT));
        meta.put("abi", contract + ".abi");
        list.add(meta);
    }

    /** 按地址查找配置中的合约（忽略大小写）；未登记返回 null */
    private Map<String, Object> contractMeta(String address) {
        if (address == null) {
            return null;
        }
        String target = address.trim().toLowerCase(Locale.ROOT);
        for (Map<String, Object> meta : contractMetas()) {
            if (target.equals(meta.get("address"))) {
                return meta;
            }
        }
        return null;
    }

    /** 链上 getCode 判断合约是否已部署；失败返回 null，不影响列表 */
    private Boolean isDeployed(Client client, String address) {
        if (client == null) {
            return null;
        }
        try {
            Code code = client.getCode(address);
            if (code == null || code.hasError()) {
                return null;
            }
            String hex = code.getCode();
            if (hex == null) {
                return null;
            }
            String normalized = hex.trim().toLowerCase(Locale.ROOT);
            return !normalized.isEmpty() && !"0x".equals(normalized) && !"0x0".equals(normalized)
                    && !"0x00".equals(normalized);
        } catch (Exception e) {
            log.warn("查询合约代码失败 address={}: {}", address, e.getMessage());
            return null;
        }
    }

    // ========== 事件解析 / 元数据 ==========

    /** 为日志行补充 eventName 与 args（未登记事件保持原样） */
    private List<Map<String, Object>> enrichLogs(List<Map<String, Object>> logs) {
        if (logs == null) {
            return List.of();
        }
        for (Map<String, Object> logRow : logs) {
            String joined = (String) logRow.get("topics");
            String data = (String) logRow.get("data");
            Map<String, Object> decoded = eventAbi.decode(splitTopics(joined), data);
            if (decoded != null) {
                logRow.put("eventName", decoded.get("eventName"));
                logRow.put("signature", decoded.get("signature"));
                logRow.put("args", decoded.get("args"));
            }
        }
        return logs;
    }

    private static List<String> splitTopics(String joined) {
        List<String> topics = new ArrayList<>();
        if (joined == null || joined.isBlank()) {
            return topics;
        }
        for (String part : joined.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                topics.add(value);
            }
        }
        return topics;
    }

    /** 链上查询 tokenURI，超时 3 秒；任何失败返回 null 且不抛出 */
    private String queryTokenUri(long tokenId) {
        NftService nftService = nftServiceProvider.getIfAvailable();
        if (nftService == null) {
            return null;
        }
        Future<String> future = chainExecutor.submit(() -> nftService.tokenURI(BigInteger.valueOf(tokenId)));
        try {
            return future.get(TOKEN_URI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            log.warn("链上查询 tokenURI 失败或超时 tokenId={}: {}", tokenId, e.getMessage());
            return null;
        }
    }

    /**
     * 从 tokenURI 提取元数据 JSON：支持直接 JSON、data:application/json 与 base64 编码。
     * 外链 URL 不做网络请求，返回 null（前端使用占位图）。
     */
    static String extractMetadataJson(String tokenUri) {
        if (tokenUri == null || tokenUri.isBlank()) {
            return null;
        }
        String value = tokenUri.trim();
        try {
            if (value.startsWith("{")) {
                return value;
            }
            if (value.startsWith("data:")) {
                int comma = value.indexOf(',');
                if (comma < 0) {
                    return null;
                }
                String payload = value.substring(comma + 1);
                if (value.substring(0, comma).toLowerCase(Locale.ROOT).contains(";base64")) {
                    return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
                }
                return URLDecoder.decode(payload, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * 抓取外链 tokenURI 的元数据 JSON。
     * 仅允许 http/https 且主机解析为公网地址（拒绝回环/内网/链路本地），
     * 限制超时与响应体大小，非 JSON 对象开头的内容直接放弃。
     */
    private String fetchRemoteMetadata(String tokenUri) {
        if (!properties.isMetadataFetchEnabled()) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(tokenUri.trim());
        } catch (Exception e) {
            return null;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return null;
        }
        if (!isPublicHost(uri.getHost())) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(Math.max(500L, properties.getMetadataTimeoutMs())))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<InputStream> response =
                    METADATA_HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return null;
                }
                byte[] bytes = in.readNBytes(properties.getMetadataMaxBytes() + 1);
                if (bytes.length > properties.getMetadataMaxBytes()) {
                    log.warn("tokenURI 元数据超过大小上限，忽略 uri={}", tokenUri);
                    return null;
                }
                String body = new String(bytes, StandardCharsets.UTF_8).trim();
                return body.startsWith("{") ? body : null;
            }
        } catch (Exception e) {
            log.warn("抓取 tokenURI 元数据失败 uri={}: {}", tokenUri, e.toString());
            return null;
        }
    }

    /** 主机是否可安全访问：必须能解析且不含回环/内网/链路本地/组播地址 */
    private static boolean isPublicHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".localhost")) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(h);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 解析元数据 JSON 为对象；解析失败返回 null */
    static Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (!node.isObject()) {
                return null;
            }
            return MAPPER.convertValue(node, MAPPER.getTypeFactory()
                    .constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 统一错误体 ==========

    /** 统一 400 中文错误体 */
    private ResponseEntity<Result<Map<String, Object>>> badRequest(String message) {
        return ResponseEntity.status(400).body(Result.fail(400, message));
    }

    /** 统一 404 中文错误体 */
    private ResponseEntity<Result<Map<String, Object>>> notFound(String message) {
        return ResponseEntity.status(404).body(Result.fail(404, message));
    }
}
