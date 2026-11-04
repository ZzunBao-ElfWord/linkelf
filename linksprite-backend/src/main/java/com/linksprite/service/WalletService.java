package com.linksprite.service;

import com.linksprite.config.FiscoBcosConfig;
import com.linksprite.dto.response.WalletResult;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 链上钱包服务：为人类用户离线生成专属外部账户（EOA）。
 * 人类 = EOA（本服务生成），AI = AIAccount 合约账户（AI 账户工厂生成），
 * 两者在链上天然区分，支撑「人类资产」与「AI 经济体」双轨体系。
 * 纯本地密钥学运算，不依赖链节点连接（dev 模式可用）。
 */
@Service
public class WalletService {

    @Autowired
    private FiscoBcosConfig fiscoConfig;

    /**
     * 生成一个新钱包。加密类型与链节点配置保持一致（默认国密 SM2）。
     * 私钥交由调用方（koko-auth）托管存储，本服务不落盘。
     */
    public synchronized WalletResult createWallet() {
        int type = "SM".equalsIgnoreCase(fiscoConfig.getCryptoType())
                ? CryptoType.SM_TYPE
                : CryptoType.ECDSA_TYPE;
        CryptoSuite suite = new CryptoSuite(type);
        CryptoKeyPair kp = suite.generateRandomKeyPair();
        return new WalletResult(kp.getAddress(), kp.getHexPrivateKey());
    }
}
