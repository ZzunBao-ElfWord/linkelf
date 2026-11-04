package com.linksprite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作品提交信息（收单）
 *
 * 一份记录 = 鲸探「新建独立发行作品」表单的一次提交 = 未来一个草稿。
 * 复杂结构（版本明细/作品数组/结算/佣金）以 JSON 文本列存储，保持与提交内容 1:1 可还原。
 */
@Data
@TableName("work_submission")
public class WorkSubmission {

    @TableId(type = IdType.AUTO)
    private Long id;

    // ===== 生成器自动产出 =====
    /** 系列唯一 ID（幂等键） */
    private String seriesId;
    /** 生成任务 ID */
    private String generatorJobId;
    /** 版本总数 */
    private Integer totalVersions;
    /** 输出格式：png / gif */
    private String format;
    /** 系列封面 URL */
    private String coverUrl;
    /** 版本明细数组（JSON） */
    private String versionsJson;

    // ===== 创作者填写 =====
    /** 创作人名称（≤18 字） */
    private String creatorName;
    /** 创作人头像 URL */
    private String creatorAvatarUrl;
    /** 创作人介绍（≤256 字） */
    private String creatorIntro;

    /** 发行类型 */
    private String issueType;
    /** 商品名称 */
    private String productName;
    /** 盲盒包含作品数 */
    private Integer blindBoxCount;
    /** 定价档位 */
    private String priceTier;
    /** 期望预售月份（YYYY-MM） */
    private String presaleMonth;
    /** 预售达标数量 */
    private String presaleThresholdQty;
    /** 预售规则（富文本） */
    private String presaleRuleHtml;

    /** 作品数组（JSON） */
    private String worksJson;
    /** 结算规则（JSON） */
    private String settlementJson;
    /** 代运营佣金（JSON） */
    private String commissionJson;

    /** 原始提交报文（JSON，全量留存，便于未来映射/对账） */
    private String rawPayload;

    /** 状态：received / draft_pending / draft_saved / failed */
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
