package com.linksprite.service;

import com.alibaba.fastjson2.JSONObject;
import com.linksprite.config.AntChainProperties;
import com.linksprite.service.AntChainGatewayClient.GatewayResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 蚂蚁链版权确权服务（UGC 流程）
 * 对接蚂蚁链鹊凿数字版权服务：作品存证、结果查询、DCI 标识申领、电子取证
 * <p>
 * 真实调用蚂蚁链开放网关；未开通（enabled=false）或方法名未配置时明确失败，
 * 不生成任何虚假的存证哈希或 DCI 号。
 */
@Slf4j
@Service
public class AntCopyrightService {

    @Autowired
    private AntChainProperties props;

    @Autowired
    private AntChainGatewayClient gatewayClient;

    /**
     * 作品版权存证（发布即确权）
     *
     * @param workHash  作品内容哈希（SHA256）
     * @param ownerInfo 权属人信息（脱敏标识，勿传敏感信息）
     */
    public CopyrightResult depositCopyright(String workHash, String ownerInfo) {
        log.info("蚂蚁链版权存证: workHash={}", workHash);
        Map<String, Object> biz = new HashMap<>();
        biz.put("workHash", workHash);
        biz.put("ownerInfo", ownerInfo);
        biz.put("depositTime", System.currentTimeMillis());

        GatewayResponse resp = gatewayClient.invoke(props.getMethods().getDeposit(), biz);
        CopyrightResult result = new CopyrightResult();
        result.setRawResponse(resp.rawBody());
        if (!resp.success()) {
            result.setSuccess(false);
            result.setMessage("鹊凿存证失败: code=" + resp.code() + " message=" + resp.message());
            return result;
        }

        String txHash = firstDeepString(resp.json(),
                "txHash", "transactionHash", "hash", "blockchainTxHash", "depositHash");
        result.setSuccess(true);
        result.setAntTxHash(txHash);
        result.setMessage("存证成功");
        return result;
    }

    /**
     * 查询存证结果
     */
    public CopyrightResult queryDeposit(String txHashOrOrderNo) {
        log.info("查询蚂蚁链存证: {}", txHashOrOrderNo);
        Map<String, Object> biz = new HashMap<>();
        biz.put("txHash", txHashOrOrderNo);
        biz.put("orderNo", txHashOrOrderNo);

        GatewayResponse resp = gatewayClient.invoke(props.getMethods().getQuery(), biz);
        CopyrightResult result = new CopyrightResult();
        result.setRawResponse(resp.rawBody());
        result.setSuccess(resp.success());
        result.setMessage(resp.success() ? "查询成功" : "查询失败: " + resp.message());
        if (resp.success()) {
            result.setAntTxHash(firstDeepString(resp.json(), "txHash", "transactionHash", "hash"));
            result.setDciId(firstDeepString(resp.json(), "dciId", "dciCode", "dci"));
        }
        return result;
    }

    /**
     * 申领 DCI 标识
     */
    public CopyrightResult applyDCI(String workHash, String ownerInfo) {
        log.info("申领DCI标识: workHash={}", workHash);
        Map<String, Object> biz = new HashMap<>();
        biz.put("workHash", workHash);
        biz.put("ownerInfo", ownerInfo);
        biz.put("applyTime", System.currentTimeMillis());

        GatewayResponse resp = gatewayClient.invoke(props.getMethods().getDci(), biz);
        CopyrightResult result = new CopyrightResult();
        result.setRawResponse(resp.rawBody());
        if (!resp.success()) {
            result.setSuccess(false);
            result.setMessage("DCI申领失败: code=" + resp.code() + " message=" + resp.message());
            return result;
        }
        result.setSuccess(true);
        result.setDciId(firstDeepString(resp.json(), "dciId", "dciCode", "dci", "copyrightNo"));
        result.setMessage("DCI 申领成功");
        return result;
    }

    /**
     * 司法/电子取证
     */
    public CopyrightResult generateForensic(String antTxHash) {
        log.info("蚂蚁链司法取证: antTxHash={}", antTxHash);
        Map<String, Object> biz = new HashMap<>();
        biz.put("txHash", antTxHash);
        biz.put("applyTime", System.currentTimeMillis());

        GatewayResponse resp = gatewayClient.invoke(props.getMethods().getForensic(), biz);
        CopyrightResult result = new CopyrightResult();
        result.setRawResponse(resp.rawBody());
        if (!resp.success()) {
            result.setSuccess(false);
            result.setMessage("取证失败: code=" + resp.code() + " message=" + resp.message());
            return result;
        }
        result.setSuccess(true);
        result.setForensicReport(firstDeepString(resp.json(),
                "reportUrl", "evidenceUrl", "forensicUrl", "certificateUrl"));
        result.setMessage("取证成功");
        return result;
    }

    /**
     * UGC 确权组合流程：先存证，再申领 DCI
     * 供 LinkSpriteNFT.mintUGC 前置使用：必须拿到存证哈希，DCI 至少已申领
     *
     * @throws com.linksprite.common.ChainException 任一环节失败则中断，禁止继续铸造
     */
    public CopyrightResult registerForUgc(String workHash, String ownerInfo) {
        CopyrightResult deposit = depositCopyright(workHash, ownerInfo);
        if (!deposit.isSuccess() || deposit.getAntTxHash() == null || deposit.getAntTxHash().isBlank()) {
            throw new com.linksprite.common.ChainException(
                    "UGC 确权前置失败：未取得蚂蚁链存证哈希。" + deposit.getMessage());
        }

        CopyrightResult dci = applyDCI(workHash, ownerInfo);
        CopyrightResult merged = new CopyrightResult();
        merged.setSuccess(true);
        merged.setAntTxHash(deposit.getAntTxHash());
        merged.setDciId(dci.isSuccess() ? dci.getDciId() : null);
        merged.setMessage(dci.isSuccess() ? "存证与DCI申领成功" : "存证成功，DCI 申领中: " + dci.getMessage());
        return merged;
    }

    /**
     * 从响应 JSON 或其 data/result 子节点中查找第一个非空字符串字段
     */
    private String firstDeepString(JSONObject json, String... keys) {
        for (String k : keys) {
            String v = json.getString(k);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        for (String node : new String[]{"data", "result", "rspContent"}) {
            JSONObject child = json.getJSONObject(node);
            if (child != null) {
                for (String k : keys) {
                    String v = child.getString(k);
                    if (v != null && !v.isBlank()) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 确权结果
     */
    @Data
    public static class CopyrightResult {
        private boolean success;
        private String message;
        /** 蚂蚁链存证哈希 */
        private String antTxHash;
        /** DCI 标识 */
        private String dciId;
        /** 取证报告 URL */
        private String forensicReport;
        /** 网关原始响应（排障用，生产环境注意脱敏） */
        private String rawResponse;
    }
}
