package com.linksprite.dto.response;

import lombok.Data;

/**
 * 链上钱包生成结果（人类用户专属 EOA 账户）
 */
@Data
public class WalletResult {

    /** 链上地址（0x 开头，作为用户专属身份与资产钱包） */
    private String address;

    /** 私钥（hex，仅平台托管的内部流转，永不对终端用户展示） */
    private String privateKey;

    public WalletResult() {
    }

    public WalletResult(String address, String privateKey) {
        this.address = address;
        this.privateKey = privateKey;
    }
}
