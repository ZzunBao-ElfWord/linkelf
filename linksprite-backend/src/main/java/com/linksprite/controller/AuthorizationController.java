package com.linksprite.controller;

import com.linksprite.common.Result;
import com.linksprite.dto.request.RegisterAuthRequest;
import com.linksprite.dto.response.ChainTxResult;
import com.linksprite.service.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;

/**
 * B端IP授权管理 API
 */
@RestController
@RequestMapping("/auth")
public class AuthorizationController {

    @Autowired
    private AuthorizationService authorizationService;

    /**
     * 登记B端IP授权
     */
    @PostMapping("/register")
    public Result<ChainTxResult> register(@Valid @RequestBody RegisterAuthRequest request) {
        ChainTxResult result = authorizationService.register(
                request.getIpOwner(),
                request.getContractNo(),
                request.getWorkHash(),
                request.getStartTime(),
                request.getEndTime(),
                request.getScope(),
                request.getMaxMint());
        return Result.success(result);
    }

    /**
     * 撤销授权
     */
    @PostMapping("/revoke/{authId}")
    public Result<ChainTxResult> revoke(@PathVariable BigInteger authId) {
        return Result.success(authorizationService.revoke(authId));
    }

    /**
     * 校验授权是否有效
     */
    @GetMapping("/valid/{authId}")
    public Result<Boolean> isValid(@PathVariable BigInteger authId) {
        return Result.success(authorizationService.isValid(authId));
    }

    /**
     * 全量授权列表（管理端授权列表页数据源）
     */
    @GetMapping("/list")
    public Result<java.util.List<java.util.Map<String, Object>>> list() {
        return Result.success(authorizationService.listAll());
    }

    /**
     * 查询授权详情
     */
    @GetMapping("/{authId}")
    public Result<String[]> getAuthorization(@PathVariable BigInteger authId) {
        return Result.success(authorizationService.getAuthorization(authId));
    }
}
