package com.linksprite.controller;

import com.linksprite.common.Result;
import com.linksprite.dto.response.ChainTxResult;
import com.linksprite.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;

/**
 * AI 智能账户与 Gas 代付 API
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    /**
     * 创建 AI 智能账户
     */
    @PostMapping("/account/create")
    public Result<ChainTxResult> createAIAccount(@RequestParam String controller) {
        return Result.success(aiService.createAIAccount(controller));
    }

    /**
     * 批量创建 AI 账户
     */
    @PostMapping("/account/create-batch")
    public Result<ChainTxResult> createAIContracts(@RequestBody String[] controllers) {
        return Result.success(aiService.createAIContracts(controllers));
    }

    /**
     * 为 AI 账户分配 SCT 算力预算
     */
    @PostMapping("/sct/allocate")
    public Result<ChainTxResult> allocateSCT(
            @RequestParam String aiAccount,
            @RequestParam BigInteger amount) {
        return Result.success(aiService.allocateSCT(aiAccount, amount));
    }

    /**
     * AI 执行操作并支付 SCT（Paymaster 代付 Gas）
     */
    @PostMapping("/execute")
    public Result<ChainTxResult> executeForAI(
            @RequestParam String aiAccount,
            @RequestParam String target,
            @RequestParam String data) {
        return Result.success(aiService.executeForAI(aiAccount, target, data));
    }

    /**
     * 设置操作定价（SCT）
     */
    @PostMapping("/price")
    public Result<ChainTxResult> setOperationPrice(
            @RequestParam String selector,
            @RequestParam BigInteger price) {
        return Result.success(aiService.setOperationPrice(selector, price));
    }

    /**
     * 查询控制器对应的 AI 账户
     */
    @GetMapping("/account/{controller}")
    public Result<String> controllerToAccount(@PathVariable String controller) {
        return Result.success(aiService.controllerToAccount(controller));
    }

    /**
     * 查询 AI 账户总数
     */
    @GetMapping("/account/count")
    public Result<BigInteger> getAIContractCount() {
        return Result.success(aiService.getAIContractCount());
    }
}
