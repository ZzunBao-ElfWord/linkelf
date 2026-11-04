-- 连接精灵链后端 · 作品提交信息（收单）建表脚本
-- 一份记录 = 鲸探「新建独立发行作品」表单的一次提交 = 未来一个草稿

CREATE TABLE IF NOT EXISTS `work_submission` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  -- 生成器自动产出
  `series_id`             VARCHAR(64)  NOT NULL COMMENT '系列唯一ID（幂等键）',
  `generator_job_id`      VARCHAR(64)  DEFAULT NULL COMMENT '生成任务ID',
  `total_versions`        INT          NOT NULL DEFAULT 0 COMMENT '版本总数',
  `format`                VARCHAR(16)  DEFAULT NULL COMMENT '输出格式 png/gif',
  `cover_url`             VARCHAR(1024) DEFAULT NULL COMMENT '系列封面URL',
  `versions_json`         JSON         DEFAULT NULL COMMENT '版本明细数组',
  -- 创作者填写
  `creator_name`          VARCHAR(32)  NOT NULL COMMENT '创作人名称（≤18字）',
  `creator_avatar_url`    VARCHAR(1024) DEFAULT NULL COMMENT '创作人头像URL',
  `creator_intro`         VARCHAR(512) DEFAULT NULL COMMENT '创作人介绍（≤256字）',
  `issue_type`            VARCHAR(64)  DEFAULT NULL COMMENT '发行类型',
  `product_name`          VARCHAR(255) DEFAULT NULL COMMENT '商品名称',
  `blind_box_count`       INT          DEFAULT NULL COMMENT '盲盒包含作品数',
  `price_tier`            VARCHAR(64)  DEFAULT NULL COMMENT '定价档位',
  `presale_month`         VARCHAR(16)  DEFAULT NULL COMMENT '期望预售月份 YYYY-MM',
  `presale_threshold_qty` VARCHAR(64)  DEFAULT NULL COMMENT '预售达标数量',
  `presale_rule_html`     TEXT         DEFAULT NULL COMMENT '预售规则（富文本）',
  `works_json`            JSON         DEFAULT NULL COMMENT '作品数组',
  `settlement_json`       JSON         DEFAULT NULL COMMENT '结算规则',
  `commission_json`       JSON         DEFAULT NULL COMMENT '代运营佣金',
  `raw_payload`           JSON         DEFAULT NULL COMMENT '原始提交报文（全量留存）',
  `status`                VARCHAR(32)  NOT NULL DEFAULT 'received' COMMENT 'received/draft_pending/draft_saved/failed',
  `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_series_id` (`series_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品提交信息（收单）';
