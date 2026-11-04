package com.linksprite.explorer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.crypto.hash.Hash;
import org.fisco.bcos.sdk.v3.crypto.hash.Keccak256;
import org.fisco.bcos.sdk.v3.crypto.hash.SM3Hash;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 基于 resources/contracts 下 ABI 建立的事件登记表。
 * <p>
 * 能力：
 * - 事件签名 → 事件名 / 参数（名称、类型、是否 indexed）映射；
 * - 事件签名 → topic0（同时登记 SM3 与 Keccak，兼容国密与标准链）；
 * - 依据 topics + data 解析已知事件参数，任何解析失败都只返回原值或 null，不抛异常。
 * <p>
 * 本类不依赖链客户端，单元测试可直接 new 使用（不连数据库、不起 Spring）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "explorer", name = "enabled", havingValue = "true")
public class ExplorerEventAbi {

    /** ABI 资源位置（classpath） */
    private static final String ABI_PATTERN = "classpath*:contracts/*.abi";

    /** 事件参数 */
    public static final class Param {
        private final String name;
        private final String type;
        private final boolean indexed;

        public Param(String name, String type, boolean indexed) {
            this.name = name;
            this.type = type;
            this.indexed = indexed;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public boolean isIndexed() {
            return indexed;
        }
    }

    /** 事件定义 */
    public static final class EventDef {
        private final String name;
        private final String signature;
        private final List<Param> params;

        EventDef(String name, String signature, List<Param> params) {
            this.name = name;
            this.signature = signature;
            this.params = params;
        }

        public String getName() {
            return name;
        }

        public String getSignature() {
            return signature;
        }

        public List<Param> getParams() {
            return params;
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, EventDef> bySignature = new LinkedHashMap<>();
    private final Map<String, EventDef> byTopic = new LinkedHashMap<>();
    private final Map<String, Set<String>> signaturesByName = new LinkedHashMap<>();

    /** 默认构造：从 classpath 加载全部 ABI；解析失败只告警，不影响服务启动 */
    public ExplorerEventAbi() {
        loadResources();
    }

    private void loadResources() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(ABI_PATTERN);
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    registerAbi(mapper.readTree(in));
                } catch (Exception e) {
                    log.warn("解析 ABI 失败 {}: {}", resource.getFilename(), e.getMessage());
                }
            }
            log.info("事件 ABI 登记完成，共 {} 个事件主题", bySignature.size());
        } catch (Exception e) {
            log.warn("加载 ABI 资源失败: {}", e.getMessage());
        }
    }

    private void registerAbi(JsonNode abi) {
        if (abi == null || !abi.isArray()) {
            return;
        }
        for (JsonNode node : abi) {
            if (!"event".equals(node.path("type").asText())) {
                continue;
            }
            String name = node.path("name").asText("");
            JsonNode inputs = node.path("inputs");
            if (name.isEmpty() || !inputs.isArray()) {
                continue;
            }
            List<Param> params = new ArrayList<>();
            StringBuilder sig = new StringBuilder(name).append('(');
            int i = 0;
            for (JsonNode input : inputs) {
                String type = canonicalType(input.path("type").asText(""));
                String paramName = input.path("name").asText("");
                boolean indexed = input.path("indexed").asBoolean(false);
                if (i > 0) {
                    sig.append(',');
                }
                sig.append(type);
                params.add(new Param(paramName, type, indexed));
                i++;
            }
            sig.append(')');
            String signature = sig.toString();

            if (bySignature.containsKey(signature)) {
                continue;
            }
            EventDef def = new EventDef(name, signature, Collections.unmodifiableList(params));
            bySignature.put(signature, def);
            signaturesByName.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(signature);
            byTopic.putIfAbsent(topicHex(new SM3Hash(), signature), def);
            byTopic.putIfAbsent(topicHex(new Keccak256(), signature), def);
        }
    }

    /** 事件名列表（去重、按字母序，供前端下拉） */
    public List<String> eventNames() {
        return new ArrayList<>(new TreeSet<>(signaturesByName.keySet()));
    }

    /** 事件名是否存在 */
    public boolean hasEvent(String eventName) {
        return eventName != null && signaturesByName.containsKey(eventName.trim());
    }

    /** 事件名对应全部 topic0（SM3 + Keccak，去掉 0x、小写） */
    public List<String> topicsOf(String eventName) {
        if (eventName == null) {
            return Collections.emptyList();
        }
        Set<String> signatures = signaturesByName.get(eventName.trim());
        if (signatures == null) {
            return Collections.emptyList();
        }
        List<String> topics = new ArrayList<>();
        for (String signature : signatures) {
            topics.add(topicHex(new SM3Hash(), signature));
            topics.add(topicHex(new Keccak256(), signature));
        }
        return topics;
    }

    /** 由 topic0 反查事件定义；未登记返回 null */
    public EventDef resolve(String topic0) {
        String topic = strip0x(topic0);
        if (topic == null) {
            return null;
        }
        return byTopic.get(topic.toLowerCase(Locale.ROOT));
    }

    /**
     * 解析一条事件日志。
     * 返回 {eventName, signature, args}；未登记事件返回 null。
     */
    public Map<String, Object> decode(List<String> topics, String dataHex) {
        try {
            String topic0 = topics == null || topics.isEmpty() ? null : strip0x(topics.get(0));
            EventDef def = topic0 == null ? null : byTopic.get(topic0.toLowerCase(Locale.ROOT));
            if (def == null) {
                return null;
            }
            Map<String, Object> args = new LinkedHashMap<>();
            String data = strip0x(dataHex);
            int topicCursor = 1;
            int head = 0;
            for (Param param : def.params) {
                Object value;
                if (param.indexed) {
                    String raw = topicCursor < topics.size() ? topics.get(topicCursor) : null;
                    topicCursor++;
                    value = decodeIndexed(param.type, raw);
                } else {
                    value = decodeData(param.type, data, head);
                    // 无论静态还是动态参数，head 均向前推进一个 32 字节槽位
                    head += 32;
                }
                args.put(param.name, value);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("eventName", def.name);
            result.put("signature", def.signature);
            result.put("args", args);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    /** 计算事件签名 topic0（SM3 与 Keccak 同时提供，供索引/检索兼容） */
    public static String topicHex(Hash hash, String signature) {
        try {
            byte[] digest = hash.hash(signature.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** indexed 参数解码：地址 / 整数 / 布尔 / bytesN；无法解码（如 indexed string）返回原值 */
    public static Object decodeIndexed(String type, String rawTopic) {
        if (rawTopic == null) {
            return null;
        }
        try {
            String word = strip0x(rawTopic);
            if (word == null || word.isEmpty()) {
                return rawTopic;
            }
            if ("address".equals(type)) {
                return word.length() >= 40 ? "0x" + word.substring(word.length() - 40).toLowerCase(Locale.ROOT) : rawTopic;
            }
            if (isUint(type) || isInt(type)) {
                return decodeInteger(type, word).toString();
            }
            if ("bool".equals(type)) {
                return new BigInteger(word, 16).signum() != 0;
            }
            if (isFixedBytes(type)) {
                int bytes = Integer.parseInt(type.substring(5));
                int chars = Math.min(bytes * 2, word.length());
                return "0x" + word.substring(0, chars).toLowerCase(Locale.ROOT);
            }
            return rawTopic;
        } catch (Exception e) {
            return rawTopic;
        }
    }

    /** 非 indexed 参数解码（按 data 中的 head 槽位）；失败返回 null */
    private static Object decodeData(String type, String data, int headByteOffset) {
        if (data == null) {
            return null;
        }
        try {
            if ("string".equals(type)) {
                return decodeString(data, headByteOffset);
            }
            if ("bytes".equals(type)) {
                return decodeBytes(data, headByteOffset);
            }
            String word = readWord(data, headByteOffset);
            if (word == null) {
                return null;
            }
            if ("address".equals(type)) {
                return "0x" + word.substring(24).toLowerCase(Locale.ROOT);
            }
            if (isUint(type) || isInt(type)) {
                return decodeInteger(type, word).toString();
            }
            if ("bool".equals(type)) {
                return new BigInteger(word, 16).signum() != 0;
            }
            if (isFixedBytes(type)) {
                int bytes = Integer.parseInt(type.substring(5));
                return "0x" + word.substring(0, Math.min(bytes * 2, 64)).toLowerCase(Locale.ROOT);
            }
            // 数组 / 结构体等复杂类型不做展开，返回原始槽位
            return "0x" + word;
        } catch (Exception e) {
            return null;
        }
    }

    private static String decodeString(String data, int headByteOffset) {
        String offsetWord = readWord(data, headByteOffset);
        if (offsetWord == null) {
            return null;
        }
        int offset = new BigInteger(offsetWord, 16).intValue();
        String lengthWord = readWord(data, offset);
        if (lengthWord == null) {
            return null;
        }
        int length = new BigInteger(lengthWord, 16).intValue();
        int startChar = offset * 2 + 64;
        if (length < 0 || startChar + length * 2 > data.length()) {
            return null;
        }
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) Integer.parseInt(data.substring(startChar + i * 2, startChar + i * 2 + 2), 16);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String decodeBytes(String data, int headByteOffset) {
        String offsetWord = readWord(data, headByteOffset);
        if (offsetWord == null) {
            return null;
        }
        int offset = new BigInteger(offsetWord, 16).intValue();
        String lengthWord = readWord(data, offset);
        int length = new BigInteger(lengthWord, 16).intValue();
        int startChar = offset * 2 + 64;
        if (length < 0 || startChar + length * 2 > data.length()) {
            return null;
        }
        return "0x" + data.substring(startChar, startChar + length * 2);
    }

    /** 读取 data 中第 headByteOffset 字节处的 32 字节槽位（返回 64 位小写十六进制） */
    private static String readWord(String data, int headByteOffset) {
        int start = headByteOffset * 2;
        if (data == null || start < 0 || start + 64 > data.length()) {
            return null;
        }
        return data.substring(start, start + 64).toLowerCase(Locale.ROOT);
    }

    /** 整数解码：int 类型按 256 位补码处理负数，uint 直接按无符号解析 */
    private static BigInteger decodeInteger(String type, String word) {
        BigInteger value = new BigInteger(word, 16);
        if (isInt(type) && !isUint(type)) {
            BigInteger max = BigInteger.ONE.shiftLeft(255);
            if (value.compareTo(max) >= 0) {
                value = value.subtract(BigInteger.ONE.shiftLeft(256));
            }
        }
        return value;
    }

    private static boolean isUint(String type) {
        return type != null && type.startsWith("uint");
    }

    private static boolean isInt(String type) {
        return type != null && type.startsWith("int") && !type.startsWith("integer");
    }

    private static boolean isFixedBytes(String type) {
        return type != null && type.startsWith("bytes") && type.length() > 5;
    }

    private static String canonicalType(String type) {
        if (type == null || type.isEmpty()) {
            return "bytes";
        }
        return switch (type) {
            case "uint" -> "uint256";
            case "int" -> "int256";
            default -> type;
        };
    }

    private static String strip0x(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return null;
        }
        return v.startsWith("0x") || v.startsWith("0X") ? v.substring(2) : v;
    }
}
