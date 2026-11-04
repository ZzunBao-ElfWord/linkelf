package com.linksprite.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.linksprite.common.ChainException;
import com.linksprite.config.AntChainProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

/**
 * 蚂蚁链开放平台风格网关客户端
 * <p>
 * 采用 appKey/appSecret 的 HMAC-SHA256 签名：
 * 1. 公共参数（app_id/method/version/format/charset/sign_type/timestamp/biz_content）
 * 2. 按参数名 ASCII 排序，拼接 key=value&amp;key=value 得到待签名串
 * 3. sign = Base64(HMAC-SHA256(appSecret, 待签名串))
 * 4. 以 application/x-www-form-urlencoded 方式 POST 到网关
 * <p>
 * 说明：鹊凿具体 method/version 由开通后的对接文档下发，在 application.yml 配置；
 * 若实际网关签名规则有差异（如 RSA2），仅需替换本类 sign() 实现。
 */
@Slf4j
@Component
public class AntChainGatewayClient {

    @Autowired
    private AntChainProperties props;

    /**
     * 调用网关方法
     *
     * @param method     方法名（鹊凿下发）
     * @param bizContent 业务参数
     * @return 网关响应（含原始报文，便于按商户文档精确解析）
     */
    public GatewayResponse invoke(String method, Map<String, Object> bizContent) {
        if (!props.isEnabled()) {
            throw new ChainException("蚂蚁链鹊凿服务未启用（antchain.quezao.enabled=false）");
        }
        if (props.getEndpoint() == null || props.getEndpoint().isBlank()) {
            throw new ChainException("未配置蚂蚁链网关地址 antchain.quezao.endpoint");
        }
        if (props.getAppKey() == null || props.getAppKey().isBlank()
                || props.getAppSecret() == null || props.getAppSecret().isBlank()) {
            throw new ChainException("未配置蚂蚁链应用凭证 antchain.quezao.app-key/app-secret");
        }
        if (method == null || method.isBlank()) {
            throw new ChainException("未配置该业务的鹊凿方法名（antchain.quezao.methods.*），请按开通文档填写");
        }

        // 1. 组装参数（TreeMap 自动按 key 排序）
        TreeMap<String, Object> params = new TreeMap<>();
        params.put("app_id", props.getAppKey());
        params.put("method", method);
        if (props.getVersion() != null && !props.getVersion().isBlank()) {
            params.put("version", props.getVersion());
        }
        if (props.getTenantId() != null && !props.getTenantId().isBlank()) {
            params.put("tenant_id", props.getTenantId());
        }
        params.put("format", props.getFormat());
        params.put("charset", props.getCharset());
        params.put("sign_type", props.getSignType());
        params.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        params.put("biz_content", JSON.toJSONString(bizContent));

        // 2. 签名
        String sign = sign(buildSignContent(params), props.getAppSecret());
        params.put("sign", sign);

        // 3. 发起请求（日志不含密钥与签名）
        log.info("调用蚂蚁链网关: method={}, endpoint={}, bizKeys={}",
                method, props.getEndpoint(), bizContent.keySet());
        String body;
        try (HttpResponse response = HttpRequest.post(props.getEndpoint())
                .form(params)
                .timeout((int) Math.max(props.getReadTimeoutMs(), props.getConnectTimeoutMs()))
                .setConnectionTimeout(props.getConnectTimeoutMs())
                .setReadTimeout(props.getReadTimeoutMs())
                .execute()) {
            body = response.body();
            if (!response.isOk()) {
                throw new ChainException("蚂蚁链网关 HTTP 状态异常: " + response.getStatus());
            }
        } catch (ChainException e) {
            throw e;
        } catch (Exception e) {
            throw new ChainException("调用蚂蚁链网关失败: " + e.getMessage(), e);
        }

        // 4. 解析响应（容错，保留原始报文）
        JSONObject parsed;
        try {
            parsed = JSON.parseObject(body);
        } catch (Exception e) {
            throw new ChainException("蚂蚁链网关返回非 JSON 报文: " + abbreviate(body), e);
        }
        return new GatewayResponse(method, body, parsed, resolveSuccess(parsed),
                resolveString(parsed, "code", "resultCode", "respCode", "subCode"),
                resolveString(parsed, "message", "msg", "subMsg", "errorMsg"));
    }

    /**
     * 构造待签名串：排序后的 key=value 以 & 连接
     */
    private String buildSignContent(TreeMap<String, Object> sortedParams) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : sortedParams.entrySet()) {
            if (e.getValue() == null || e.getValue().toString().isEmpty() || "sign".equals(e.getKey())) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * HMAC-SHA256 签名并 Base64
     */
    private String sign(String content, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new ChainException("蚂蚁链网关签名失败: " + e.getMessage(), e);
        }
    }

    /**
     * 宽容判定成功（不同产品线返回码不同），原始报文仍保留供精确解析
     */
    private boolean resolveSuccess(JSONObject resp) {
        Boolean flag = resp.getBoolean("success");
        if (flag != null) {
            return flag;
        }
        String code = firstNonBlank(resp, "code", "resultCode", "respCode");
        if (code != null) {
            return "10000".equals(code) || "200".equals(code) || "0".equals(code)
                    || "SUCCESS".equalsIgnoreCase(code) || "OK".equalsIgnoreCase(code);
        }
        // 存在业务数据节点也视为成功
        return resp.containsKey("data") || resp.containsKey("result");
    }

    private String resolveString(JSONObject resp, String... keys) {
        for (String k : keys) {
            Object v = resp.get(k);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }

    private String firstNonBlank(JSONObject resp, String... keys) {
        return resolveString(resp, keys);
    }

    private String abbreviate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    /**
     * URL 编码工具（供扩展使用）
     */
    public static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 网关响应
     */
    public record GatewayResponse(String method, String rawBody, JSONObject json,
                                  boolean success, String code, String message) {
    }
}
