package com.linksprite.controller;

import com.linksprite.common.Result;
import com.linksprite.tenant.Tenant;
import com.linksprite.tenant.TenantException;
import com.linksprite.tenant.TenantStore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户管理接口（由 ApiKeyFilter 的 X-Ops-Token 保护，路径 /internal/tenants）。
 * <p>
 * 安全约定：明文 key 只出现在「创建」与「轮换」的响应体中，且仅显示一次；
 * 日志与其它接口一律不打印明文 key。
 */
@Slf4j
@RestController
@RequestMapping("/internal/tenants")
public class TenantAdminController {

    private final TenantStore tenantStore;

    public TenantAdminController(TenantStore tenantStore) {
        this.tenantStore = tenantStore;
    }

    /** 租户列表（仅 keyHint，不含完整 keyHash） */
    @GetMapping
    public Result<List<Tenant>> list() {
        return Result.success(tenantStore.list());
    }

    /** 创建租户，返回一次性明文 key */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody CreateRequest request) {
        String key = tenantStore.create(
                request.getProductId(),
                request.getName(),
                request.getQps() == null ? 50 : request.getQps(),
                request.getDailyQuota() == null ? 100000 : request.getDailyQuota(),
                request.getScopes());
        log.info("租户创建完成 productId={}", request.getProductId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productId", request.getProductId());
        data.put("apiKey", key);
        data.put("message", "API-Key 仅在此响应中显示一次，请立即妥善保存（服务端只保存哈希）");
        return Result.success(data);
    }

    /** 轮换密钥：旧 key 立即失效 */
    @PostMapping("/{productId}/rotate")
    public Result<Map<String, Object>> rotate(@PathVariable String productId) {
        String key = tenantStore.rotate(productId);
        log.info("租户密钥轮换完成 productId={}", productId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productId", productId);
        data.put("apiKey", key);
        data.put("message", "新 API-Key 仅在此响应中显示一次，旧 key 已立即失效");
        return Result.success(data);
    }

    /** 吊销租户 */
    @PostMapping("/{productId}/revoke")
    public Result<Map<String, Object>> revoke(@PathVariable String productId) {
        tenantStore.revoke(productId);
        log.info("租户已吊销 productId={}", productId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productId", productId);
        data.put("status", Tenant.STATUS_REVOKED);
        data.put("message", "租户已吊销，该密钥立即失效");
        return Result.success(data);
    }

    /** 从磁盘重新加载租户文件 */
    @PostMapping("/reload")
    public Result<Map<String, Object>> reload() {
        tenantStore.reload();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", tenantStore.list().size());
        data.put("message", "租户文件已重新加载");
        return Result.success(data);
    }

    /** 租户异常统一转换为对应 HTTP 状态 */
    @ExceptionHandler(TenantException.class)
    public ResponseEntity<Map<String, Object>> handleTenantException(TenantException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", errorOf(e.getStatus()));
        body.put("message", e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(body);
    }

    private String errorOf(int status) {
        return switch (status) {
            case 400 -> "invalid_request";
            case 404 -> "not_found";
            case 409 -> "conflict";
            default -> "error";
        };
    }

    /** 创建租户请求体 */
    @Data
    public static class CreateRequest {
        private String productId;
        private String name;
        private Integer qps;
        private Integer dailyQuota;
        private List<String> scopes;
    }
}
