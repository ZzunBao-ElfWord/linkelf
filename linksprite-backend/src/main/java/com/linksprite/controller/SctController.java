package com.linksprite.controller;

import com.linksprite.common.Result;
import com.linksprite.dto.response.ChainTxResult;
import com.linksprite.service.SctService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;

/**
 * SCT 算力词元 API
 */
@RestController
@RequestMapping("/sct")
public class SctController {

    @Autowired
    private SctService sctService;

    /**
     * 平台铸造 SCT 给 AI 账户
     */
    @PostMapping("/mint")
    public Result<ChainTxResult> mintToAI(
            @RequestParam String aiAccount,
            @RequestParam BigInteger amount) {
        return Result.success(sctService.mintToAI(aiAccount, amount));
    }

    /**
     * 销毁 AI 账户的 SCT
     */
    @PostMapping("/burn")
    public Result<ChainTxResult> burnFromAI(
            @RequestParam String aiAccount,
            @RequestParam BigInteger amount) {
        return Result.success(sctService.burnFromAI(aiAccount, amount));
    }

    /**
     * AI 支付服务费
     */
    @PostMapping("/pay-fee")
    public Result<ChainTxResult> payServiceFee(
            @RequestParam String aiAccount,
            @RequestParam BigInteger amount) {
        return Result.success(sctService.payServiceFee(aiAccount, amount));
    }

    /**
     * 查询 SCT 余额
     */
    @GetMapping("/balance/{account}")
    public Result<BigInteger> balanceOf(@PathVariable String account) {
        return Result.success(sctService.balanceOf(account));
    }

    /**
     * 查询是否 AI 账户
     */
    @GetMapping("/is-ai/{account}")
    public Result<Boolean> isAIContract(@PathVariable String account) {
        return Result.success(sctService.isAIContract(account));
    }

    /**
     * 查询总供应量
     */
    @GetMapping("/totalSupply")
    public Result<BigInteger> totalSupply() {
        return Result.success(sctService.totalSupply());
    }
}
