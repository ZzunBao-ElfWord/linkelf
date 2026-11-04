package com.linksprite.service;

import com.linksprite.contract.TransferPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

/**
 * NFT合规流转服务
 * 转赠冷却期 + 频次限制
 */
@Slf4j
@Service
public class TransferService {

    @Autowired
    private ChainStatusService chainStatus;

    @Autowired(required = false)
    private TransferPolicy transferPolicy;

    /**
     * 校验是否可以转赠
     */
    public boolean canTransfer(String from, String to, BigInteger tokenId) {
        chainStatus.requireConnected();
        return transferPolicy.canTransfer(from, to, tokenId);
    }

    /**
     * 记录转赠行为（NFT转账成功后调用）
     */
    public void recordTransfer(String from) {
        chainStatus.requireConnected();
        transferPolicy.recordTransfer(from);
    }

    /**
     * 查询转赠状态
     */
    public String[] getTransferStatus(String addr) {
        chainStatus.requireConnected();
        return transferPolicy.getTransferStatus(addr).toArray(new String[0]);
    }
}
