package com.linksprite.explorer;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 轻量区块链浏览器配置。
 * <p>
 * 默认关闭（EXPLORER_ENABLED 未设置时为 false），避免影响生产环境现有功能。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "explorer")
public class ExplorerProperties {

    /** 是否启用浏览器（索引器 + REST + 前端数据源） */
    private boolean enabled = false;

    /** 轮询间隔（毫秒），作为 @Scheduled fixedDelay 使用 */
    private long pollMs = 2000;

    /** 冷启动回溯区块数：首次启动时从 latest - backfill 开始索引 */
    private int backfillBlocks = 100;

    /** 展示用群组 ID（默认 0） */
    private String groupId = "0";

    /** tokenURI 为外链时是否由服务端抓取元数据 JSON（带超时与大小上限） */
    private boolean metadataFetchEnabled = true;

    /** 外链元数据抓取超时（毫秒） */
    private long metadataTimeoutMs = 3000L;

    /** 外链元数据响应体上限（字节），超出则放弃解析 */
    private int metadataMaxBytes = 256 * 1024;
}
