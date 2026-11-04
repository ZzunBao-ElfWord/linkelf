package com.linksprite.service;

import com.linksprite.contract.AIAccountFactory;
import com.linksprite.contract.Paymaster;
import com.linksprite.dto.response.ChainTxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

/**
 * AI 智能账户与 Gas 代付服务
 */
@Slf4j
@Service
public class AiService extends BaseService {

    @Autowired
    private ChainStatusService chainStatus;

    @Autowired(required = false)
    private AIAccountFactory aiAccountFactory;

    @Autowired(required = false)
    private Paymaster paymaster;

    @Autowired
    private SctService sctService;

    /**
     * 创建 AI 智能账户
     */
    public ChainTxResult createAIAccount(String controller) {
        requireChain(chainStatus);
        log.info("创建AI账户: controller={}", controller);
        return checkTxResult(aiAccountFactory.createAIAccount(controller));
    }

    /**
     * 批量创建 AI 账户
     */
    public ChainTxResult createAIContracts(String[] controllers) {
        requireChain(chainStatus);
        return checkTxResult(aiAccountFactory.createAIContracts(controllers));
    }

    /**
     * 为 AI 账户分配 SCT 算力预算
     */
    public ChainTxResult allocateSCT(String aiAccount, BigInteger amount) {
        requireChain(chainStatus);
        return sctService.mintToAI(aiAccount, amount);
    }

    /**
     * AI 执行操作并支付 SCT（Paymaster 代付 Gas）
     */
    public ChainTxResult executeForAI(String aiAccount, String target, String data) {
        requireChain(chainStatus);
        log.info("AI执行操作: aiAccount={}, target={}", aiAccount, target);
        return checkTxResult(paymaster.executeForAI(aiAccount, target, data));
    }

    /**
     * 设置操作定价（SCT）
     */
    public ChainTxResult setOperationPrice(String selector, BigInteger price) {
        requireChain(chainStatus);
        return checkTxResult(paymaster.setOperationPrice(selector, price));
    }

    // ========== 查询 ==========

    public String controllerToAccount(String controller) {
        requireChain(chainStatus);
        return aiAccountFactory.controllerToAccount(controller);
    }

    public BigInteger getAIContractCount() {
        requireChain(chainStatus);
        return aiAccountFactory.getAIContractCount();
    }
}
