package com.linksprite.service;

import com.linksprite.contract.SpriteComputeToken;
import com.linksprite.dto.response.ChainTxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

/**
 * SCT 算力词元服务
 */
@Slf4j
@Service
public class SctService extends BaseService {

    @Autowired
    private ChainStatusService chainStatus;

    @Autowired(required = false)
    private SpriteComputeToken spriteComputeToken;

    public ChainTxResult mintToAI(String aiAccount, BigInteger amount) {
        requireChain(chainStatus);
        log.info("铸造SCT给AI: aiAccount={}, amount={}", aiAccount, amount);
        return checkTxResult(spriteComputeToken.mintToAI(aiAccount, amount));
    }

    public ChainTxResult burnFromAI(String aiAccount, BigInteger amount) {
        requireChain(chainStatus);
        log.info("销毁AI的SCT: aiAccount={}, amount={}", aiAccount, amount);
        return checkTxResult(spriteComputeToken.burnFromAI(aiAccount, amount));
    }

    public ChainTxResult payServiceFee(String aiAccount, BigInteger amount) {
        requireChain(chainStatus);
        return checkTxResult(spriteComputeToken.payServiceFee(aiAccount, amount));
    }

    public ChainTxResult transfer(String to, BigInteger amount) {
        requireChain(chainStatus);
        return checkTxResult(spriteComputeToken.transfer(to, amount));
    }

    public ChainTxResult approve(String spender, BigInteger amount) {
        requireChain(chainStatus);
        return checkTxResult(spriteComputeToken.approve(spender, amount));
    }

    public ChainTxResult registerAIContract(String aiAccount) {
        requireChain(chainStatus);
        return checkTxResult(spriteComputeToken.registerAIContract(aiAccount));
    }

    // ========== 查询 ==========

    public BigInteger balanceOf(String account) {
        requireChain(chainStatus);
        return spriteComputeToken.balanceOf(account);
    }

    public boolean isAIContract(String account) {
        requireChain(chainStatus);
        return spriteComputeToken.isAIContract(account);
    }

    public BigInteger totalSupply() {
        requireChain(chainStatus);
        return spriteComputeToken.totalSupply();
    }
}
