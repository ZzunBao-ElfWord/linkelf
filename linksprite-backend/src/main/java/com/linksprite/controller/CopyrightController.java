package com.linksprite.controller;

import com.linksprite.common.Result;
import com.linksprite.dto.request.CopyrightDepositRequest;
import com.linksprite.service.AntCopyrightService;
import com.linksprite.service.AntCopyrightService.CopyrightResult;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 版权确权 API（蚂蚁链鹊凿）
 * 未开通时服务返回明确错误，不伪造确权结果
 */
@Slf4j
@RestController
@RequestMapping("/copyright")
public class CopyrightController {

    @Autowired
    private AntCopyrightService antCopyrightService;

    /**
     * 作品存证（发布即确权）
     */
    @PostMapping("/deposit")
    public Result<CopyrightResult> deposit(@Valid @RequestBody CopyrightDepositRequest request) {
        return Result.success(
                antCopyrightService.depositCopyright(request.getWorkHash(), request.getOwnerInfo()));
    }

    /**
     * UGC 确权组合流程：存证 + DCI 申领，结果可直接用于 NFT.mintUGC
     */
    @PostMapping("/register-ugc")
    public Result<CopyrightResult> registerUgc(@Valid @RequestBody CopyrightDepositRequest request) {
        return Result.success(
                antCopyrightService.registerForUgc(request.getWorkHash(), request.getOwnerInfo()));
    }

    /**
     * 查询存证结果
     */
    @GetMapping("/query")
    public Result<CopyrightResult> query(@RequestParam String txHashOrOrderNo) {
        return Result.success(antCopyrightService.queryDeposit(txHashOrOrderNo));
    }

    /**
     * 司法/电子取证
     */
    @PostMapping("/forensic")
    public Result<CopyrightResult> forensic(@RequestParam String antTxHash) {
        return Result.success(antCopyrightService.generateForensic(antTxHash));
    }
}
