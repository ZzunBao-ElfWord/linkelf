package com.linksprite.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 版权确权请求
 */
@Data
public class CopyrightDepositRequest {

    /** 作品内容哈希（SHA256，由平台对作品文件/元数据计算） */
    @NotBlank(message = "作品哈希不能为空")
    private String workHash;

    /** 权属人信息（脱敏标识，如平台用户ID；勿传真实证件号等敏感信息） */
    @NotBlank(message = "权属人信息不能为空")
    private String ownerInfo;
}
