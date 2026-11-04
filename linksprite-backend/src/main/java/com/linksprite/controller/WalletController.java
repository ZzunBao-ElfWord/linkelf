package com.linksprite.controller;

import com.linksprite.common.Result;
import com.linksprite.dto.response.WalletResult;
import com.linksprite.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 链上钱包 API（人类用户）
 * 仅供服务端（koko-auth）携带 X-API-Key 调用，用于注册/登录时为用户生成专属链上地址。
 */
@RestController
@RequestMapping("/wallet")
public class WalletController {

    @Autowired
    private WalletService walletService;

    /**
     * 生成一个新钱包：返回链上地址 + 私钥（私钥仅平台托管，永不下发给终端用户）
     */
    @PostMapping("/create")
    public Result<WalletResult> create() {
        return Result.success(walletService.createWallet());
    }
}
