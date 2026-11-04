package com.linksprite.tenant;

/**
 * 租户管理异常：携带 HTTP 状态码，供管理接口转换为对应响应。
 */
public class TenantException extends RuntimeException {

    private final int status;

    public TenantException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
