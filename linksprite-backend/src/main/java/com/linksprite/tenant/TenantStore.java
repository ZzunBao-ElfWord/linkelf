package com.linksprite.tenant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 租户存储：使用 JSON 文件持久化（无数据库依赖）。
 * <p>
 * - 文件路径取自环境变量 LINKSPRITE_TENANTS_FILE（默认 tenants.json），目录不存在会自动创建。
 * - 启动时加载；文件缺失视为空列表。
 * - 写入使用「临时文件 + ATOMIC_MOVE」，方法级 synchronized 保证并发安全。
 * - 服务端只保存明文 key 的 SHA-256，绝不落盘明文。
 */
@Slf4j
@Component
public class TenantStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** productId：小写字母 / 数字 / 短横，长度 1~32 */
    private static final Pattern PRODUCT_ID_PATTERN = Pattern.compile("^[a-z0-9-]{1,32}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String filePath;
    private final List<Tenant> tenants = new ArrayList<>();

    public TenantStore(@Value("${app.tenants-file:tenants.json}") String filePath) {
        this.filePath = filePath;
        reload();
    }

    /**
     * 列表：不回显完整 keyHash，仅保留 keyHint。
     */
    public synchronized List<Tenant> list() {
        List<Tenant> result = new ArrayList<>(tenants.size());
        for (Tenant t : tenants) {
            Tenant copy = copyOf(t);
            copy.setKeyHash(null);
            result.add(copy);
        }
        return result;
    }

    /**
     * 按 key 的 SHA-256 摘要查找租户（含已吊销，由调用方判断状态）。
     */
    public synchronized Tenant findByKeyHash(String keyHash) {
        if (keyHash == null || keyHash.isBlank()) {
            return null;
        }
        for (Tenant t : tenants) {
            if (keyHash.equals(t.getKeyHash())) {
                return copyOf(t);
            }
        }
        return null;
    }

    /**
     * 创建租户并返回一次性明文 key（格式 lsk_&lt;productId&gt;_&lt;32 hex&gt;）。
     * productId 冲突抛 409。
     */
    public synchronized String create(String productId, String name, int qps, int dailyQuota, List<String> scopes) {
        validateProductId(productId);
        validateQpsAndQuota(qps, dailyQuota);
        validateScopes(scopes);
        for (Tenant t : tenants) {
            if (t.getProductId().equals(productId)) {
                throw new TenantException(409, "productId 已存在: " + productId);
            }
        }

        String key = generateKey(productId);
        Tenant tenant = new Tenant();
        tenant.setProductId(productId);
        tenant.setName(name == null || name.isBlank() ? productId : name.trim());
        tenant.setKeyHash(sha256Hex(key));
        tenant.setKeyHint(keyHint(key));
        tenant.setScopes(scopes == null || scopes.isEmpty()
                ? new ArrayList<>(List.of(ScopeResolver.READ))
                : new ArrayList<>(scopes));
        tenant.setQps(qps);
        tenant.setDailyQuota(dailyQuota);
        tenant.setStatus(Tenant.STATUS_ACTIVE);
        tenant.setCreatedAt(Instant.now().toString());
        tenant.setRotatedAt(null);

        tenants.add(tenant);
        persist();
        log.info("租户已创建 productId={}, scopes={}, qps={}, dailyQuota={}",
                productId, tenant.getScopes(), qps, dailyQuota);
        return key;
    }

    /**
     * 轮换密钥：旧 key 立即失效，更新 rotatedAt，返回新明文 key。
     */
    public synchronized String rotate(String productId) {
        Tenant tenant = requireTenant(productId);
        String key = generateKey(productId);
        tenant.setKeyHash(sha256Hex(key));
        tenant.setKeyHint(keyHint(key));
        tenant.setRotatedAt(Instant.now().toString());
        persist();
        log.info("租户密钥已轮换 productId={}", productId);
        return key;
    }

    /**
     * 吊销租户：此后该 key 一律 401。
     */
    public synchronized void revoke(String productId) {
        Tenant tenant = requireTenant(productId);
        tenant.setStatus(Tenant.STATUS_REVOKED);
        persist();
        log.info("租户已吊销 productId={}", productId);
    }

    /**
     * 从磁盘重新加载（文件缺失视为空列表）。
     */
    public synchronized void reload() {
        Path path = Paths.get(filePath);
        tenants.clear();
        if (!Files.exists(path)) {
            log.info("租户文件不存在，按空列表处理: {}", path.toAbsolutePath());
            return;
        }
        try {
            List<Tenant> loaded = MAPPER.readValue(path.toFile(), new TypeReference<List<Tenant>>() {
            });
            if (loaded != null) {
                tenants.addAll(loaded);
            }
            log.info("租户文件已加载: {}，共 {} 个租户", path.toAbsolutePath(), tenants.size());
        } catch (IOException e) {
            throw new IllegalStateException("租户文件解析失败: " + path.toAbsolutePath() + "，原因: " + e.getMessage(), e);
        }
    }

    /**
     * 计算 SHA-256 十六进制小写摘要。
     */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    /** 临时文件 + ATOMIC_MOVE 原子落盘 */
    private void persist() {
        Path path = Paths.get(filePath);
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = Paths.get(filePath + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), tenants);
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // 个别文件系统不支持 ATOMIC_MOVE 时退化为普通覆盖移动
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("租户文件写入失败: " + e.getMessage(), e);
        }
    }

    private Tenant requireTenant(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new TenantException(400, "productId 不能为空");
        }
        for (Tenant t : tenants) {
            if (t.getProductId().equals(productId)) {
                return t;
            }
        }
        throw new TenantException(404, "productId 不存在: " + productId);
    }

    private void validateProductId(String productId) {
        if (productId == null || !PRODUCT_ID_PATTERN.matcher(productId).matches()) {
            throw new TenantException(400, "productId 必须为小写字母/数字/短横，长度 1~32");
        }
    }

    private void validateQpsAndQuota(int qps, int dailyQuota) {
        if (qps <= 0) {
            throw new TenantException(400, "qps 必须为正整数");
        }
        if (dailyQuota <= 0) {
            throw new TenantException(400, "dailyQuota 必须为正整数");
        }
    }

    private void validateScopes(List<String> scopes) {
        if (scopes == null) {
            return;
        }
        for (String scope : scopes) {
            if (scope == null || !ScopeResolver.ALL_SCOPES.contains(scope)) {
                throw new TenantException(400, "未知 scope: " + scope + "，可选值: " + ScopeResolver.ALL_SCOPES);
            }
        }
    }

    /** 生成 lsk_&lt;productId&gt;_&lt;32 hex&gt; 明文 key */
    private static String generateKey(String productId) {
        return "lsk_" + productId + "_" + randomHex(32);
    }

    /** 生成指定长度的小写十六进制串 */
    private static String randomHex(int length) {
        byte[] buf = new byte[(length + 1) / 2];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(buf.length * 2);
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.substring(0, length);
    }

    /** 密钥提示：前6…后4 */
    private static String keyHint(String key) {
        if (key == null || key.length() <= 10) {
            return key;
        }
        return key.substring(0, 6) + "…" + key.substring(key.length() - 4);
    }

    /** 深拷贝，避免外部修改内存数据 */
    private static Tenant copyOf(Tenant t) {
        Tenant copy = new Tenant();
        copy.setProductId(t.getProductId());
        copy.setName(t.getName());
        copy.setKeyHash(t.getKeyHash());
        copy.setKeyHint(t.getKeyHint());
        copy.setScopes(t.getScopes() == null ? new ArrayList<>() : new ArrayList<>(t.getScopes()));
        copy.setQps(t.getQps());
        copy.setDailyQuota(t.getDailyQuota());
        copy.setStatus(t.getStatus());
        copy.setCreatedAt(t.getCreatedAt());
        copy.setRotatedAt(t.getRotatedAt());
        return copy;
    }
}
