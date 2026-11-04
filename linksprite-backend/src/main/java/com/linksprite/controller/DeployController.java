package com.linksprite.controller;

import com.linksprite.common.ChainException;
import com.linksprite.common.Result;
import com.linksprite.dto.response.ContractDeploymentResult;
import com.linksprite.service.ContractDeploymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 合约部署管理 API（仅在 fisco.enabled=true 时加载）
 * <p>
 * 部署属于高危链上操作，额外受 fisco.deploy-enabled 开关保护，默认关闭
 */
@Slf4j
@RestController
@RequestMapping("/deploy")
public class DeployController {

    @Autowired(required = false)
    private ContractDeploymentService deploymentService;

    @Value("${fisco.deploy-enabled:false}")
    private boolean deployEnabled;

    /**
     * 部署前置检查：各合约 BIN 是否就位
     */
    @GetMapping("/precheck")
    public Result<Map<String, Object>> precheck() {
        requireDeployService();
        Map<String, Boolean> bins = deploymentService.precheck();
        long ready = bins.values().stream().filter(Boolean::booleanValue).count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deployEnabled", deployEnabled);
        data.put("binReady", ready + "/" + bins.size());
        data.put("bins", bins);
        data.put("allReady", ready == bins.size());
        return Result.success(data);
    }

    /**
     * 按依赖顺序部署全部 7 个合约
     */
    @PostMapping("/contracts")
    public Result<ContractDeploymentResult> deployAll() {
        requireDeployService();
        if (!deployEnabled) {
            throw new ChainException(
                    "合约部署接口未开启（fisco.deploy-enabled=false）。确认链环境就绪后，在配置中显式开启再调用");
        }
        log.warn("收到合约部署请求，将在链上部署 7 个合约，操作不可撤销");
        return Result.success(deploymentService.deployAll());
    }

    private void requireDeployService() {
        if (deploymentService == null) {
            throw new ChainException("区块链未启用（fisco.enabled=false），无法执行部署");
        }
    }
}
