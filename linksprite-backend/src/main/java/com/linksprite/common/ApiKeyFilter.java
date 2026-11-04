package com.linksprite.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linksprite.tenant.RateLimiter;
import com.linksprite.tenant.ScopeResolver;
import com.linksprite.tenant.Tenant;
import com.linksprite.tenant.TenantStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多租户 API-Key 鉴权过滤器。
 *
 * 规则：
 * - /api/health（及其变体）与 /api 根索引直接放行（容器/反代探活）。
 * - /internal/** 使用请求头 X-Ops-Token 与 LINKSPRITE_OPS_TOKEN 常量时间比较；
 *   未配置 ops token 时返回 503 OPS_DISABLED。
 * - 其余 /api/**：X-API-Key → SHA-256 → 匹配租户；匹配不到时兼容旧部署：
 *   LINKSPRITE_API_KEY 已配置且明文相等，则视为 productId=legacy、拥有全部 scope。
 * - 租户 revoked → 401；scope 不匹配 → 403；qps 或日配额超限 → 429。
 * - 通过后写入 request 属性 productId，并回写响应头 X-LinkSprite-Product。
 * - 每个请求结束追加一行审计 JSON，按天轮转；审计 IO 异常只告警不影响请求。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String HEADER_OPS_TOKEN = "X-Ops-Token";
    private static final String HEADER_PRODUCT = "X-LinkSprite-Product";
    /** Servlet API 未定义 429 常量，这里显式声明 */
    private static final int SC_TOO_MANY_REQUESTS = 429;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TenantStore tenantStore;
    private final RateLimiter rateLimiter;
    private final String legacyKey;
    private final String opsToken;
    private final String auditLogPath;
    /** 旧全局 key 对应的虚拟租户（拥有全部 scope、不限流） */
    private final Tenant legacyTenant;
    /** 审计文件写入锁 */
    private final Object auditLock = new Object();

    public ApiKeyFilter(TenantStore tenantStore,
                        RateLimiter rateLimiter,
                        @Value("${linksprite.api-key:}") String legacyKey,
                        @Value("${app.ops-token:}") String opsToken,
                        @Value("${app.audit-log:logs/api-audit.jsonl}") String auditLogPath) {
        this.tenantStore = tenantStore;
        this.rateLimiter = rateLimiter;
        this.legacyKey = legacyKey;
        this.opsToken = opsToken;
        this.auditLogPath = auditLogPath;
        this.legacyTenant = buildLegacyTenant();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String productId = "unknown";
        try {
            String uri = request.getRequestURI();
            String relPath = relativePath(uri, request.getContextPath());
            String apiPath = apiPath(relPath);

            // 1. 健康检查、根索引与公开浏览器：直接放行
            if (isPublicPath(apiPath) || isExplorerPath(apiPath)) {
                productId = "public";
                chain.doFilter(request, response);
                return;
            }

            // 2. 内部运维接口：ops token 保护
            if (isInternalPath(relPath)) {
                productId = "ops";
                if (opsToken == null || opsToken.isBlank()) {
                    writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                            "ops_disabled", "运维令牌未配置，内部接口已关闭");
                    return;
                }
                String provided = request.getHeader(HEADER_OPS_TOKEN);
                if (!constantTimeEquals(opsToken, provided)) {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "unauthorized", "运维令牌无效或缺失");
                    return;
                }
                chain.doFilter(request, response);
                return;
            }

            // 3. 其余 /api/**：租户 API-Key 鉴权
            String rawKey = request.getHeader(HEADER_API_KEY);
            if (rawKey == null || rawKey.isBlank()) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "unauthorized", "API-Key 无效或缺失");
                return;
            }
            String trimmedKey = rawKey.trim();
            Tenant tenant = tenantStore.findByKeyHash(TenantStore.sha256Hex(trimmedKey));
            if (tenant == null && legacyKey != null && !legacyKey.isBlank()
                    && constantTimeEquals(legacyKey, trimmedKey)) {
                // 兼容旧部署：全局 key 视为 legacy 产品，拥有全部权限
                tenant = legacyTenant;
            }
            if (tenant == null) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "unauthorized", "API-Key 无效或缺失");
                return;
            }
            productId = tenant.getProductId();

            if (Tenant.STATUS_REVOKED.equals(tenant.getStatus())) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "unauthorized", "该产品密钥已被吊销");
                return;
            }

            String scope = ScopeResolver.resolve(apiPath);
            if (tenant.getScopes() == null || !tenant.getScopes().contains(scope)) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        "forbidden", "当前产品无权访问该接口（缺少 scope: " + scope + "）");
                return;
            }

            rateLimiter.setQpsLimit(productId, tenant.getQps());
            if (!rateLimiter.allowQps(productId)) {
                writeError(response, SC_TOO_MANY_REQUESTS,
                        "rate_limited", "请求频率超出限制（每秒 " + tenant.getQps() + " 次）");
                return;
            }
            if (!rateLimiter.allowDaily(productId, tenant.getDailyQuota())) {
                writeError(response, SC_TOO_MANY_REQUESTS,
                        "rate_limited", "今日调用配额已用尽（上限 " + tenant.getDailyQuota() + "）");
                return;
            }

            request.setAttribute("productId", productId);
            response.setHeader(HEADER_PRODUCT, productId);
            chain.doFilter(request, response);
        } finally {
            audit(productId, request, response, System.currentTimeMillis() - start);
        }
    }

    /** 健康检查、根索引及其变体 */
    private boolean isPublicPath(String apiPath) {
        return "/api".equals(apiPath)
                || "/api/".equals(apiPath)
                || "/api/health".equals(apiPath)
                || "/api/health/".equals(apiPath)
                || apiPath.startsWith("/api/health/");
    }

    /** /internal/**（去掉 context-path 后的相对路径） */
    private boolean isInternalPath(String relPath) {
        return "/internal".equals(relPath) || relPath.startsWith("/internal/");
    }

    /** 公开只读的轻量区块链浏览器（无需 API-Key） */
    private boolean isExplorerPath(String apiPath) {
        return "/api/explorer".equals(apiPath)
                || "/api/explorer/".equals(apiPath)
                || apiPath.startsWith("/api/explorer/");
    }

    /** 去掉 context-path；context-path 为空时原样返回 */
    private String relativePath(String uri, String contextPath) {
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /** 统一成 /api/... 形式，便于 scope 映射 */
    private String apiPath(String relPath) {
        if (relPath == null || relPath.isEmpty()) {
            return "/api";
        }
        return relPath.startsWith("/api") ? relPath : "/api" + relPath;
    }

    /** 常量时间比较（先转字节，避免长度/内容侧信道） */
    private boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    /** 统一中文错误体 */
    private void writeError(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }

    /** 旧全局 key 的虚拟租户 */
    private Tenant buildLegacyTenant() {
        Tenant t = new Tenant();
        t.setProductId("legacy");
        t.setName("legacy");
        t.setScopes(new ArrayList<>(ScopeResolver.ALL_SCOPES));
        // 旧部署无配额概念，这里置为不限流
        t.setQps(Integer.MAX_VALUE);
        t.setDailyQuota(Integer.MAX_VALUE);
        t.setStatus(Tenant.STATUS_ACTIVE);
        return t;
    }

    /** 审计日志：按天轮转追加 JSON 行，异常仅告警 */
    private void audit(String productId, HttpServletRequest request, HttpServletResponse response, long durationMs) {
        try {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("ts", Instant.now().toString());
            line.put("productId", productId);
            line.put("method", request.getMethod());
            line.put("uri", request.getRequestURI());
            line.put("status", response.getStatus());
            line.put("durationMs", durationMs);
            line.put("ip", clientIp(request));
            String json = MAPPER.writeValueAsString(line) + System.lineSeparator();

            Path file = auditFileForToday();
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            synchronized (auditLock) {
                Files.write(file, json.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            log.warn("审计日志写入失败（不影响请求）: {}", e.toString());
        }
    }

    /** 按天轮转：logs/api-audit.jsonl → logs/api-audit-2026-10-30.jsonl */
    private Path auditFileForToday() {
        String base = auditLogPath == null || auditLogPath.isBlank() ? "logs/api-audit.jsonl" : auditLogPath;
        String prefix = base.endsWith(".jsonl") ? base.substring(0, base.length() - ".jsonl".length()) : base;
        return Paths.get(prefix + "-" + LocalDate.now() + ".jsonl");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
