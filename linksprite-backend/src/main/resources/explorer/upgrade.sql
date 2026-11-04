-- ============================================================
-- 轻量区块链浏览器 —— P2 增量升级脚本（幂等，可重复执行）
-- 适用：已在线上运行的 linkelf_explorer 索引库
-- 执行：mysql -u root -p linkelf_explorer < upgrade.sql
-- 说明：只新增列/索引，不修改既有列，不影响索引器写入
-- ============================================================

SET @db := DATABASE();

-- ---------- eb_tokens：tokenURI 与元数据缓存 ----------
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'eb_tokens' AND COLUMN_NAME = 'token_uri'
);
SET @sql := IF(@has_col = 0,
    'ALTER TABLE eb_tokens ADD COLUMN token_uri VARCHAR(512) DEFAULT NULL COMMENT ''链上 tokenURI 缓存''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'eb_tokens' AND COLUMN_NAME = 'metadata_json'
);
SET @sql := IF(@has_col = 0,
    'ALTER TABLE eb_tokens ADD COLUMN metadata_json MEDIUMTEXT DEFAULT NULL COMMENT ''tokenURI 解析出的元数据 JSON 缓存''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- eb_logs：事件检索索引 ----------
SET @has_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'eb_logs' AND INDEX_NAME = 'idx_log_topic'
);
SET @sql := IF(@has_idx = 0,
    'ALTER TABLE eb_logs ADD KEY idx_log_topic (topic0)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'eb_logs' AND INDEX_NAME = 'idx_log_block'
);
SET @sql := IF(@has_idx = 0,
    'ALTER TABLE eb_logs ADD KEY idx_log_block (block_number)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
