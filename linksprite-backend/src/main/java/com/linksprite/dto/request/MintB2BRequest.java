package com.linksprite.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigInteger;

/**
 * B端NFT铸造请求
 */
@Data
public class MintB2BRequest {

    @NotBlank(message = "接收地址不能为空")
    private String to;

    /** 留空则由服务端自动分配（totalSupply+1） */
    private BigInteger tokenId;

    @NotBlank(message = "tokenURI不能为空")
    private String tokenURI;

    @NotNull(message = "授权ID不能为空")
    private BigInteger authId;

    private String ipfsHash;
    private String workHash;
}
