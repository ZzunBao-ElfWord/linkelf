package com.linksprite.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 作品提交信息（收单）请求
 *
 * 对应「统一标准」schema：生成器产出 + 创作者填写。
 */
@Data
public class WorkSubmissionRequest {

    // ===== 生成器自动产出 =====
    @NotBlank(message = "系列ID不能为空")
    private String seriesId;

    private String generatorJobId;

    @NotNull(message = "版本总数不能为空")
    private Integer totalVersions;

    private String format;
    private String coverUrl;

    /** 版本明细：[{index, assetUrl, assetSha256, filename, rarityScore, rarityTier, traits}] */
    private List<Map<String, Object>> versions;

    // ===== 创作人信息 =====
    @NotBlank(message = "创作人名称不能为空")
    @Size(max = 18, message = "创作人名称不能超过18字")
    private String creatorName;

    private String creatorAvatarUrl;

    @Size(max = 256, message = "创作人介绍不能超过256字")
    private String creatorIntro;

    // ===== 商品发行规则 =====
    private String issueType;

    @NotBlank(message = "商品名称不能为空")
    private String productName;

    private Integer blindBoxCount;
    private String priceTier;
    private String presaleMonth;
    private String presaleThresholdQty;
    private String presaleRuleHtml;

    // ===== 作品数组（可重复「盲盒」块） =====
    private List<Map<String, Object>> works;

    // ===== 结算规则 =====
    private List<Map<String, Object>> settlementTiers;

    // ===== 代运营佣金 =====
    private Map<String, Object> commission;
}
