package com.linksprite.controller;

import com.linksprite.common.Result;
import com.linksprite.service.ChainStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查与服务信息 API
 */
@RestController
public class HealthController {

    @Autowired
    private ChainStatusService chainStatus;

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "linksprite-backend");
        info.put("status", "UP");
        info.put("chainEnabled", chainStatus.isEnabled());
        info.put("chainConnected", chainStatus.isConnected());
        info.put("chainId", chainStatus.getChainId());
        info.put("groupId", chainStatus.getGroupId());
        info.put("blockNumber", chainStatus.getBlockNumber());
        return Result.success(info);
    }

    /**
     * API 索引
     */
    @GetMapping
    public Result<Map<String, Object>> index() {
        Map<String, Object> api = new LinkedHashMap<>();
        api.put("name", "连接精灵链后端服务");
        api.put("version", "1.0.0");

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("GET  /health", "健康检查与链状态");
        endpoints.put("POST /auth/register", "登记B端IP授权");
        endpoints.put("GET  /auth/valid/{authId}", "校验授权有效性");
        endpoints.put("POST /nft/mint/b2b", "B端铸造NFT");
        endpoints.put("POST /nft/mint/ugc", "UGC铸造NFT");
        endpoints.put("POST /nft/transfer", "转赠NFT");
        endpoints.put("GET  /nft/owner/{tokenId}", "查询NFT所有者");
        endpoints.put("GET  /nft/proof/{tokenId}", "查询权利证明");
        endpoints.put("POST /kyc/verify", "实名认证");
        endpoints.put("GET  /kyc/verified/{user}", "查询实名状态");
        endpoints.put("POST /sct/mint", "铸造SCT给AI");
        endpoints.put("GET  /sct/balance/{account}", "查询SCT余额");
        endpoints.put("POST /ai/account/create", "创建AI账户");
        endpoints.put("POST /ai/execute", "AI执行操作(Paymaster代付)");
        api.put("endpoints", endpoints);

        return Result.success(api);
    }
}
