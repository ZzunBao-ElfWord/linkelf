package com.linksprite.service;

import com.linksprite.common.ChainException;
import com.linksprite.contract.LinkSpriteNFT;
import com.linksprite.dto.response.ChainTxResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

/**
 * NFT 铸造与查询服务
 * 支持 B2B（授权铸造）和 UGC（蚂蚁确权铸造）双来源
 */
@Slf4j
@Service
public class NftService extends BaseService {

    @Autowired
    private ChainStatusService chainStatus;

    @Autowired(required = false)
    private LinkSpriteNFT linkSpriteNFT;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private KycService kycService;

    /**
     * B端铸造NFT（绑定授权）
     * tokenId 为 null 时自动分配（totalSupply+1，链上无销毁，自增安全）
     */
    public ChainTxResult mintB2B(
            String to, BigInteger tokenId, String tokenURI,
            BigInteger authId, String ipfsHash, String workHash) {
        requireChain(chainStatus);
        if (tokenId == null) {
            tokenId = linkSpriteNFT.totalSupply().add(BigInteger.ONE);
            log.info("tokenId 未指定，自动分配: {}", tokenId);
        }
        log.info("B2B铸造NFT: tokenId={}, authId={}, to={}", tokenId, authId, to);

        if (!authorizationService.isValid(authId)) {
            throw new ChainException("授权无效或已过期，authId=" + authId);
        }

        ChainTxResult result = checkTxResult(linkSpriteNFT.mintB2B(
                to, tokenId, tokenURI, authId, ipfsHash, workHash));

        try {
            authorizationService.incrementMintedCount(authId);
        } catch (Exception e) {
            log.warn("增加授权铸造计数失败（需手动补偿）: authId={}, error={}", authId, e.getMessage());
        }

        return result;
    }

    /**
     * UGC铸造NFT
     */
    public ChainTxResult mintUGC(
            String to, BigInteger tokenId, String tokenURI,
            String dciId, String antTxHash, String ipfsHash, String workHash) {
        requireChain(chainStatus);
        log.info("UGC铸造NFT: tokenId={}, dciId={}, creator={}", tokenId, dciId, to);

        if (!kycService.isVerified(to)) {
            throw new ChainException("创作者未实名认证: " + to);
        }
        if (dciId == null || dciId.isEmpty()) {
            throw new ChainException("DCI标识不能为空");
        }
        if (antTxHash == null || antTxHash.isEmpty()) {
            throw new ChainException("蚂蚁链存证哈希不能为空");
        }

        return checkTxResult(linkSpriteNFT.mintUGC(
                to, tokenId, tokenURI, dciId, antTxHash, ipfsHash, workHash));
    }

    /**
     * 安全转账（受限转赠）
     */
    public ChainTxResult safeTransferFrom(String from, String to, BigInteger tokenId) {
        requireChain(chainStatus);
        log.info("NFT转赠: tokenId={}, from={}, to={}", tokenId, from, to);
        return checkTxResult(linkSpriteNFT.safeTransferFrom(from, to, tokenId));
    }

    /**
     * 授权
     */
    public ChainTxResult approve(String to, BigInteger tokenId) {
        requireChain(chainStatus);
        return checkTxResult(linkSpriteNFT.approve(to, tokenId));
    }

    /**
     * 暂停/恢复NFT流转
     */
    public ChainTxResult setPaused(BigInteger tokenId, boolean paused) {
        requireChain(chainStatus);
        return checkTxResult(linkSpriteNFT.setPaused(tokenId, paused));
    }

    // ========== 查询 ==========

    public String ownerOf(BigInteger tokenId) {
        requireChain(chainStatus);
        return linkSpriteNFT.ownerOf(tokenId);
    }

    public BigInteger balanceOf(String owner) {
        requireChain(chainStatus);
        return linkSpriteNFT.balanceOf(owner);
    }

    public String tokenURI(BigInteger tokenId) {
        requireChain(chainStatus);
        return linkSpriteNFT.tokenURI(tokenId);
    }

    public String[] getProofInfo(BigInteger tokenId) {
        requireChain(chainStatus);
        return linkSpriteNFT.getProofInfo(tokenId).toArray(new String[0]);
    }

    public BigInteger totalSupply() {
        requireChain(chainStatus);
        return linkSpriteNFT.totalSupply();
    }
}
