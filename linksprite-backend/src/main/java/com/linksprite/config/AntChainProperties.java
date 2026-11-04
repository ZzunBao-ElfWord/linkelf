package com.linksprite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 蚂蚁链鹊凿数字版权服务配置
 * <p>
 * 鹊凿 REST 网关在商务开通后，由控制台/对接文档下发以下信息：
 * - endpoint：网关地址
 * - appKey / appSecret：应用凭证
 * - 各业务接口的 method 方法名与 version
 * 未开通前保持 enabled=false，调用确权接口会明确报错，不会伪造结果。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "antchain.quezao")
public class AntChainProperties {

    /** 是否启用蚂蚁链确权（未开通保持 false） */
    private boolean enabled = false;

    /** 网关地址，开通后下发，例如 https://openapi.antchain.antgroup.com */
    private String endpoint = "";

    /** 应用 Key */
    private String appKey = "";

    /** 应用密钥（仅用于网关签名，禁止落库/出现在日志） */
    private String appSecret = "";

    /** 签名算法：HMAC-SHA256（appKey/appSecret 模式） */
    private String signType = "HMAC-SHA256";

    /** 接口版本号（随鹊凿对接文档填写） */
    private String version = "";

    /** 租户/机构 ID（部分接口需要） */
    private String tenantId = "";

    /** 业务参数序列化格式：JSON */
    private String format = "JSON";

    /** 字符集 */
    private String charset = "utf-8";

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 5000;

    /** 读取超时（毫秒） */
    private int readTimeoutMs = 15000;

    /** 各业务方法名（开通后按对接文档填写） */
    private Methods methods = new Methods();

    @Data
    public static class Methods {
        /** 版权存证登记 */
        private String deposit = "";
        /** 存证结果查询 */
        private String query = "";
        /** DCI 标识申领 */
        private String dci = "";
        /** 电子取证/司法取证 */
        private String forensic = "";
    }
}
