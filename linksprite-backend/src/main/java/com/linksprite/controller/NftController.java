package com.linksprite.controller;

import com.linksprite.common.Result;
import com.linksprite.dto.request.MintB2BRequest;
import com.linksprite.dto.request.MintUGCRequest;
import com.linksprite.dto.response.ChainTxResult;
import com.linksprite.service.NftService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;

/**
 * NFT 铸造与查询 API
 */
@RestController
@RequestMapping("/nft")
public class NftController {

    @Autowired
    private NftService nftService;

    /**
     * B端铸造NFT（绑定授权）
     */
    @PostMapping("/mint/b2b")
    public Result<ChainTxResult> mintB2B(@Valid @RequestBody MintB2BRequest request) {
        ChainTxResult result = nftService.mintB2B(
                request.getTo(),
                request.getTokenId(),
                request.getTokenURI(),
                request.getAuthId(),
                request.getIpfsHash(),
                request.getWorkHash());
        return Result.success(result);
    }

    /**
     * UGC铸造NFT（绑定蚂蚁确权）
     */
    @PostMapping("/mint/ugc")
    public Result<ChainTxResult> mintUGC(@Valid @RequestBody MintUGCRequest request) {
        ChainTxResult result = nftService.mintUGC(
                request.getTo(),
                request.getTokenId(),
                request.getTokenURI(),
                request.getDciId(),
                request.getAntTxHash(),
                request.getIpfsHash(),
                request.getWorkHash());
        return Result.success(result);
    }

    /**
     * 转赠NFT（受限转赠）
     */
    @PostMapping("/transfer")
    public Result<ChainTxResult> transfer(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigInteger tokenId) {
        return Result.success(nftService.safeTransferFrom(from, to, tokenId));
    }

    /**
     * 查询NFT所有者
     */
    @GetMapping("/owner/{tokenId}")
    public Result<String> ownerOf(@PathVariable BigInteger tokenId) {
        return Result.success(nftService.ownerOf(tokenId));
    }

    /**
     * 查询地址持有数量
     */
    @GetMapping("/balance/{owner}")
    public Result<BigInteger> balanceOf(@PathVariable String owner) {
        return Result.success(nftService.balanceOf(owner));
    }

    /**
     * 查询NFT元数据URI
     */
    @GetMapping("/uri/{tokenId}")
    public Result<String> tokenURI(@PathVariable BigInteger tokenId) {
        return Result.success(nftService.tokenURI(tokenId));
    }

    /**
     * 查询NFT权利证明信息
     * 返回：[source, proofType, proofId]
     */
    @GetMapping("/proof/{tokenId}")
    public Result<String[]> getProofInfo(@PathVariable BigInteger tokenId) {
        return Result.success(nftService.getProofInfo(tokenId));
    }

    /**
     * 暂停/恢复NFT流转
     */
    @PostMapping("/pause/{tokenId}")
    public Result<ChainTxResult> setPaused(
            @PathVariable BigInteger tokenId,
            @RequestParam boolean paused) {
        return Result.success(nftService.setPaused(tokenId, paused));
    }

    /**
     * 查询总供应量
     */
    @GetMapping("/totalSupply")
    public Result<BigInteger> totalSupply() {
        return Result.success(nftService.totalSupply());
    }
}
