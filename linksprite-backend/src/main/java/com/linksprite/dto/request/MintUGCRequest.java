package com.linksprite.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigInteger;

/**
 * UGC NFT铸造请求
 * 前置：作品已通过蚂蚁链确权（dciId + antTxHash）
 */
@Data
public class MintUGCRequest {

    @NotBlank(message = "创作者地址不能为空")
    private String to;

    @NotNull(message = "tokenId不能为空")
    private BigInteger tokenId;

    @NotBlank(message = "tokenURI不能为空")
    private String tokenURI;

    @NotBlank(message = "DCI标识不能为空")
    private String dciId;

    @NotBlank(message = "蚂蚁链存证哈希不能为空")
    private String antTxHash;

    private String ipfsHash;
    private String workHash;
}
