package com.linksprite.tenant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 租户（产品）模型：一个产品对应一把 API-Key，用于多租户鉴权。
 * <p>
 * 注意：keyHash 为明文 key 的 SHA-256 十六进制小写摘要，仅用于比对；
 * keyHint 只保留「前6…后4」用于运营侧识别，任何列表接口都不回显完整 keyHash。
 */
@Data
public class Tenant {

    /** 启用状态 */
    public static final String STATUS_ACTIVE = "active";
    /** 已吊销状态 */
    public static final String STATUS_REVOKED = "revoked";

    /** 产品标识：小写字母 / 数字 / 短横，长度 ≤32 */
    private String productId;

    /** 产品名称 */
    private String name;

    /** 明文 key 的 SHA-256（hex 小写），不对外展示 */
    private String keyHash;

    /** 密钥提示：前6…后4 */
    private String keyHint;

    /** 授权范围（见 ScopeResolver） */
    private List<String> scopes = new ArrayList<>();

    /** 每秒请求上限，默认 50 */
    private int qps = 50;

    /** 每日调用配额，默认 100000 */
    private int dailyQuota = 100000;

    /** 状态：active | revoked */
    private String status = STATUS_ACTIVE;

    /** 创建时间（ISO-8601） */
    private String createdAt;

    /** 最近一次轮换时间（ISO-8601），未轮换为 null */
    private String rotatedAt;
}
