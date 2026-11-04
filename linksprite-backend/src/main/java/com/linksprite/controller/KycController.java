package com.linksprite.controller;

import com.linksprite.common.Result;
import com.linksprite.dto.response.ChainTxResult;
import com.linksprite.service.KycService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 实名认证 API
 */
@RestController
@RequestMapping("/kyc")
public class KycController {

    @Autowired
    private KycService kycService;

    /**
     * 实名认证
     */
    @PostMapping("/verify")
    public Result<ChainTxResult> verify(
            @RequestParam String user,
            @RequestParam String proofHash) {
        return Result.success(kycService.verify(user, proofHash));
    }

    /**
     * 撤销实名
     */
    @PostMapping("/revoke/{user}")
    public Result<ChainTxResult> revoke(@PathVariable String user) {
        return Result.success(kycService.revoke(user));
    }

    /**
     * 查询实名状态
     */
    @GetMapping("/verified/{user}")
    public Result<Boolean> isVerified(@PathVariable String user) {
        return Result.success(kycService.isVerified(user));
    }
}
