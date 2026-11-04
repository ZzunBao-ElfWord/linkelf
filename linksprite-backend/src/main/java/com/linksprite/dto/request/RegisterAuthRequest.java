package com.linksprite.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigInteger;

/**
 * B端IP授权登记请求
 */
@Data
public class RegisterAuthRequest {

    @NotBlank(message = "IP方地址不能为空")
    private String ipOwner;

    @NotBlank(message = "合同编号不能为空")
    private String contractNo;

    @NotBlank(message = "作品哈希不能为空")
    private String workHash;

    @NotNull(message = "生效时间不能为空")
    private BigInteger startTime;

    @NotNull(message = "到期时间不能为空")
    private BigInteger endTime;

    /** 授权范围：0=独家, 1=非独家, 2=可转授权 */
    @NotNull(message = "授权范围不能为空")
    private Integer scope;

    @NotNull(message = "最大铸造数量不能为空")
    private BigInteger maxMint;
}
