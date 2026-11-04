package com.linksprite.service;

import com.linksprite.contract.KYCRegistry;
import com.linksprite.dto.response.ChainTxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 实名认证服务
 */
@Slf4j
@Service
public class KycService extends BaseService {

    @Autowired
    private ChainStatusService chainStatus;

    @Autowired(required = false)
    private KYCRegistry kycRegistry;

    /**
     * 标记用户已实名
     */
    public ChainTxResult verify(String user, String proofHash) {
        requireChain(chainStatus);
        log.info("实名认证: user={}", user);
        return checkTxResult(kycRegistry.verify(user, proofHash));
    }

    /**
     * 撤销实名
     */
    public ChainTxResult revoke(String user) {
        requireChain(chainStatus);
        log.info("撤销实名: user={}", user);
        return checkTxResult(kycRegistry.revoke(user));
    }

    /**
     * 校验用户是否已实名
     */
    public boolean isVerified(String user) {
        requireChain(chainStatus);
        return kycRegistry.isVerified(user);
    }
}
