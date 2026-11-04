package com.linksprite.service;

import com.linksprite.common.ChainException;
import com.linksprite.config.FiscoBcosConfig;
import com.linksprite.contract.ContractAbis;
import com.linksprite.dto.response.ContractDeploymentResult;
import com.linksprite.dto.response.ContractDeploymentResult.DeployedContract;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.v3.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合约部署服务
 * <p>
 * 严格按依赖顺序在真实链上部署 7 个合约：
 * <pre>
 * 1 KYCRegistry(platform)
 * 2 TransferPolicy(platform)
 * 3 AuthorizationRegistry(platform)
 * 4 LinkSpriteNFT(platform, authRegistry, transferPolicy, kycRegistry)
 * 5 SpriteComputeToken(platform)
 * 6 AIAccountFactory(platform, sctToken)
 * 7 Paymaster(platform, sctToken)
 * 部署后额外执行 SpriteComputeToken.setAIFactory(AIAccountFactory)
 * 与 TransferPolicy.setNftContract(LinkSpriteNFT)
 * </pre>
 * BIN 来自 solc 编译产物 classpath:/contracts/*.bin；缺失时明确失败，不模拟部署。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "fisco", name = "enabled", havingValue = "true")
public class ContractDeploymentService {

    @Autowired
    private Client client;

    @Autowired
    private CryptoKeyPair cryptoKeyPair;

    @Autowired
    private FiscoBcosConfig fiscoBcosConfig;

    /**
     * 部署前置检查：所有 BIN 是否齐全
     */
    public Map<String, Boolean> precheck() {
        Map<String, Boolean> status = new HashMap<>();
        for (String name : ContractAbis.DEPLOY_ORDER) {
            status.put(name, ContractAbis.binExists(name));
        }
        return status;
    }

    /**
     * 按依赖顺序部署全部合约
     */
    public ContractDeploymentResult deployAll() {
        // 1. 前置校验 BIN
        List<String> missing = new ArrayList<>();
        for (String name : ContractAbis.DEPLOY_ORDER) {
            if (!ContractAbis.binExists(name)) {
                missing.add("contracts/" + name + ".bin");
            }
        }
        if (!missing.isEmpty()) {
            throw new ChainException("合约字节码缺失，无法部署: " + String.join(", ", missing)
                    + "。请先运行 deploy/compile_contracts.sh 或 compile_contracts.ps1 编译合约");
        }

        String platform = cryptoKeyPair.getAddress();
        log.info("========== 开始按顺序部署合约，platform={} 群组={} ==========",
                platform, fiscoBcosConfig.getGroupId());

        AssembleTransactionProcessor processor =
                TransactionProcessorFactory.createAssembleTransactionProcessor(client, cryptoKeyPair);

        ContractDeploymentResult result = new ContractDeploymentResult();
        result.setPlatform(platform);
        result.setGroupId(fiscoBcosConfig.getGroupId());

        Map<String, String> addr = new HashMap<>();

        // 2. 按顺序部署
        deploy(processor, result, "KYCRegistry",
                Collections.singletonList(platform), addr);
        deploy(processor, result, "TransferPolicy",
                Collections.singletonList(platform), addr);
        deploy(processor, result, "AuthorizationRegistry",
                Collections.singletonList(platform), addr);
        deploy(processor, result, "LinkSpriteNFT",
                java.util.Arrays.asList(platform,
                        addr.get("AuthorizationRegistry"),
                        addr.get("TransferPolicy"),
                        addr.get("KYCRegistry")),
                addr);
        deploy(processor, result, "SpriteComputeToken",
                Collections.singletonList(platform), addr);
        deploy(processor, result, "AIAccountFactory",
                java.util.Arrays.asList(platform, addr.get("SpriteComputeToken")), addr);
        deploy(processor, result, "Paymaster",
                java.util.Arrays.asList(platform, addr.get("SpriteComputeToken")), addr);

        // 3. 部署后绑定：SCT 注册 AI 账户工厂、TransferPolicy 绑定 NFT 合约、AuthorizationRegistry 绑定 NFT 合约
        bindSctFactory(processor, addr, result);
        bindNftContract(processor, addr, result);
        bindAuthRegistryNft(processor, addr, result);

        // 4. 生成可直接粘贴的配置片段
        result.setConfigSnippet(buildConfigSnippet(addr));

        log.info("========== 全部合约部署完成 ==========\n{}", result.getConfigSnippet());
        return result;
    }

    /**
     * 部署单个合约
     */
    private void deploy(AssembleTransactionProcessor processor,
                        ContractDeploymentResult result,
                        String name,
                        List<Object> constructorArgs,
                        Map<String, String> addrMap) {
        String abi = ContractAbis.getAbi(name);
        String bin = ContractAbis.getBin(name);

        log.info("[部署] {} 构造参数={}", name, constructorArgs);
        TransactionResponse response;
        try {
            response = processor.deployAndGetResponse(abi, bin, constructorArgs);
        } catch (Exception e) {
            throw new ChainException("部署合约 " + name + " 失败: " + e.getMessage(), e);
        }

        TransactionReceipt receipt = response.getTransactionReceipt();
        if (receipt == null || !receipt.isStatusOK()) {
            String status = receipt == null ? "null" : String.valueOf(receipt.getStatus());
            String message = receipt == null ? "无回执" : receipt.getMessage();
            String txHash = receipt == null ? null : receipt.getTransactionHash();
            throw new ChainException("合约 " + name + " 部署上链失败: status=" + status
                    + " message=" + message, txHash, receipt == null ? null : receipt.getStatus());
        }

        String contractAddress = receipt.getContractAddress();
        if (contractAddress == null || contractAddress.isEmpty() || "0x0".equals(contractAddress)) {
            throw new ChainException("合约 " + name + " 部署成功但未返回合约地址，txHash="
                    + receipt.getTransactionHash());
        }

        addrMap.put(name, contractAddress);
        result.getContracts().add(new DeployedContract(name, contractAddress, receipt.getTransactionHash()));
        log.info("[部署成功] {} => {} txHash={}", name, contractAddress, receipt.getTransactionHash());
    }

    /**
     * 调用 SpriteComputeToken.setAIFactory(AIAccountFactory)
     */
    private void bindSctFactory(AssembleTransactionProcessor processor,
                                Map<String, String> addr,
                                ContractDeploymentResult result) {
        String sct = addr.get("SpriteComputeToken");
        String factory = addr.get("AIAccountFactory");
        try {
            TransactionResponse resp = processor.sendTransactionAndGetResponse(
                    sct, ContractAbis.getAbi("SpriteComputeToken"),
                    "setAIFactory",
                    Collections.singletonList(factory));
            TransactionReceipt receipt = resp.getTransactionReceipt();
            log.warn("[SCT绑定调试] returnCode={}, returnMessage={}, receipt={}",
                    resp.getReturnCode(), resp.getReturnMessage(), receipt);
            if (receipt == null || !receipt.isStatusOK()) {
                String detail = receipt == null
                        ? "receipt=null, returnCode=" + resp.getReturnCode()
                        + ", returnMessage=" + resp.getReturnMessage()
                        : "status=" + receipt.getStatus()
                        + ", message=" + receipt.getMessage()
                        + ", output=" + receipt.getOutput();
                log.error("[SCT绑定失败详情] {}", detail);
                // 合约均已部署成功，绑定失败不阻断结果返回，仅记录待办
                result.getWiring().add("SpriteComputeToken.setAIFactory(" + factory + ") 未完成: " + detail);
                return;
            }
            result.getWiring().add("SpriteComputeToken.setAIFactory(" + factory + ") => tx "
                    + receipt.getTransactionHash());
            log.info("SCT 已绑定 AI 工厂: {}", factory);
        } catch (Exception e) {
            log.error("[SCT绑定异常] {}", e.getMessage(), e);
            result.getWiring().add("SpriteComputeToken.setAIFactory(" + factory + ") 异常: " + e.getMessage());
        }
    }

    /**
     * 调用 TransferPolicy.setNftContract(LinkSpriteNFT)
     * 绑定后 NFT 合约转账时原子调用 recordTransfer，月频次限制生效
     */
    private void bindNftContract(AssembleTransactionProcessor processor,
                                 Map<String, String> addr,
                                 ContractDeploymentResult result) {
        String policy = addr.get("TransferPolicy");
        String nft = addr.get("LinkSpriteNFT");
        try {
            TransactionResponse resp = processor.sendTransactionAndGetResponse(
                    policy, ContractAbis.getAbi("TransferPolicy"),
                    "setNftContract",
                    Collections.singletonList(nft));
            TransactionReceipt receipt = resp.getTransactionReceipt();
            if (receipt == null || !receipt.isStatusOK()) {
                String detail = receipt == null
                        ? "receipt=null, returnCode=" + resp.getReturnCode()
                        : "status=" + receipt.getStatus() + ", message=" + receipt.getMessage();
                log.error("[TransferPolicy绑定NFT失败] {}", detail);
                result.getWiring().add("TransferPolicy.setNftContract(" + nft + ") 未完成: " + detail);
                return;
            }
            result.getWiring().add("TransferPolicy.setNftContract(" + nft + ") => tx "
                    + receipt.getTransactionHash());
            log.info("TransferPolicy 已绑定 NFT 合约: {}", nft);
        } catch (Exception e) {
            log.error("[TransferPolicy绑定NFT异常] {}", e.getMessage(), e);
            result.getWiring().add("TransferPolicy.setNftContract(" + nft + ") 异常: " + e.getMessage());
        }
    }

    /**
     * 调用 AuthorizationRegistry.setNftContract(LinkSpriteNFT)
     * 绑定后 NFT 合约铸造时原子调用 incrementMintedCount，授权铸造计数生效
     */
    private void bindAuthRegistryNft(AssembleTransactionProcessor processor,
                                     Map<String, String> addr,
                                     ContractDeploymentResult result) {
        String authRegistry = addr.get("AuthorizationRegistry");
        String nft = addr.get("LinkSpriteNFT");
        try {
            TransactionResponse resp = processor.sendTransactionAndGetResponse(
                    authRegistry, ContractAbis.getAbi("AuthorizationRegistry"),
                    "setNftContract",
                    Collections.singletonList(nft));
            TransactionReceipt receipt = resp.getTransactionReceipt();
            if (receipt == null || !receipt.isStatusOK()) {
                String detail = receipt == null
                        ? "receipt=null, returnCode=" + resp.getReturnCode()
                        : "status=" + receipt.getStatus() + ", message=" + receipt.getMessage();
                log.error("[AuthorizationRegistry绑定NFT失败] {}", detail);
                result.getWiring().add("AuthorizationRegistry.setNftContract(" + nft + ") 未完成: " + detail);
                return;
            }
            result.getWiring().add("AuthorizationRegistry.setNftContract(" + nft + ") => tx "
                    + receipt.getTransactionHash());
            log.info("AuthorizationRegistry 已绑定 NFT 合约: {}", nft);
        } catch (Exception e) {
            log.error("[AuthorizationRegistry绑定NFT异常] {}", e.getMessage(), e);
            result.getWiring().add("AuthorizationRegistry.setNftContract(" + nft + ") 异常: " + e.getMessage());
        }
    }

    /**
     * 生成 application.yml 合约地址配置片段
     */
    private String buildConfigSnippet(Map<String, String> addr) {
        return """
                contract:
                  kyc-registry: "%s"
                  transfer-policy: "%s"
                  authorization-registry: "%s"
                  linksprite-nft: "%s"
                  sprite-compute-token: "%s"
                  ai-account-factory: "%s"
                  paymaster: "%s"
                """.formatted(
                addr.get("KYCRegistry"),
                addr.get("TransferPolicy"),
                addr.get("AuthorizationRegistry"),
                addr.get("LinkSpriteNFT"),
                addr.get("SpriteComputeToken"),
                addr.get("AIAccountFactory"),
                addr.get("Paymaster"));
    }
}
