-- ============================================================
-- 轻量区块链浏览器（MariaDB / MySQL）DDL
-- 使用方式：
--   mysql -u root -p < schema.sql
-- 说明：索引器不会自动建库，请先执行本脚本再开启 EXPLORER_ENABLED=true
-- 已有库升级（P2 新增 token_uri / metadata_json 与日志索引）：
--   mysql -u root -p linkelf_explorer < upgrade.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS linkelf_explorer
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE linkelf_explorer;

-- 区块
CREATE TABLE IF NOT EXISTS eb_blocks (
    number      BIGINT       NOT NULL COMMENT '区块高度',
    block_hash  VARCHAR(66)  NOT NULL COMMENT '区块哈希',
    parent_hash VARCHAR(66)  DEFAULT NULL COMMENT '父区块哈希',
    `timestamp` BIGINT       NOT NULL COMMENT '出块时间（秒级时间戳）',
    tx_count    INT          NOT NULL DEFAULT 0 COMMENT '交易数',
    gas_used    BIGINT       DEFAULT NULL COMMENT '本块 gas 消耗',
    sealer      VARCHAR(255) DEFAULT NULL COMMENT '出块共识节点（FISCO 3.x 为 consensusNodeIds 字符串）',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区块表';

-- 交易
CREATE TABLE IF NOT EXISTS eb_txs (
    hash         VARCHAR(66)  NOT NULL COMMENT '交易哈希',
    block_number BIGINT       NOT NULL COMMENT '所在区块',
    tx_index     INT          NOT NULL COMMENT '块内序号',
    from_addr    VARCHAR(66)  DEFAULT NULL COMMENT '发送方',
    to_addr      VARCHAR(66)  DEFAULT NULL COMMENT '接收方（合约调用时为合约地址）',
    value        TEXT         DEFAULT NULL COMMENT '转账金额（十进制字符串）',
    gas_used     BIGINT       DEFAULT NULL COMMENT 'gas 消耗',
    status       INT          DEFAULT NULL COMMENT '0=成功，其它=失败',
    input        MEDIUMTEXT   DEFAULT NULL COMMENT '交易 input（含 4 字节方法选择器）',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (hash),
    KEY idx_tx_block (block_number),
    KEY idx_tx_from (from_addr),
    KEY idx_tx_to (to_addr)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易表';

-- 事件日志
CREATE TABLE IF NOT EXISTS eb_logs (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    tx_hash      VARCHAR(66) DEFAULT NULL COMMENT '交易哈希',
    log_index    INT         DEFAULT NULL COMMENT '回执内日志序号',
    address      VARCHAR(66) DEFAULT NULL COMMENT '事件发出合约',
    topic0       VARCHAR(66) DEFAULT NULL COMMENT '事件签名 topic',
    topics       MEDIUMTEXT  DEFAULT NULL COMMENT '全部 topic，逗号分隔',
    data         MEDIUMTEXT  DEFAULT NULL COMMENT '事件 data',
    block_number BIGINT      DEFAULT NULL COMMENT '所在区块',
    PRIMARY KEY (id),
    KEY idx_log_tx (tx_hash),
    KEY idx_log_addr (address),
    KEY idx_log_topic (topic0),
    KEY idx_log_block (block_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件日志表';

-- NFT 持有（来自 ERC721 Transfer from=0x0）
CREATE TABLE IF NOT EXISTS eb_tokens (
    token_id     BIGINT      NOT NULL COMMENT 'tokenId',
    contract     VARCHAR(66) DEFAULT NULL COMMENT 'NFT 合约地址',
    owner        VARCHAR(66) DEFAULT NULL COMMENT '当前持有人',
    mint_tx      VARCHAR(66) DEFAULT NULL COMMENT '铸造交易哈希',
    block_number BIGINT      DEFAULT NULL COMMENT '铸造区块',
    block_time   BIGINT      DEFAULT NULL COMMENT '铸造时间（秒级时间戳）',
    product_hint VARCHAR(32) DEFAULT NULL COMMENT '产品标识（由授权 contractNo 前缀推导）',
    token_uri    VARCHAR(512) DEFAULT NULL COMMENT '链上 tokenURI 缓存',
    metadata_json MEDIUMTEXT  DEFAULT NULL COMMENT 'tokenURI 解析出的元数据 JSON 缓存',
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (token_id),
    KEY idx_token_owner (owner),
    KEY idx_token_product (product_hint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NFT 铸造/持有表';

-- IP 授权登记（AuthorizationRegistry.AuthorizationRegistered）
CREATE TABLE IF NOT EXISTS eb_auths (
    auth_id      BIGINT       NOT NULL COMMENT '授权 ID',
    ip_owner     VARCHAR(66)  DEFAULT NULL COMMENT 'IP 方地址',
    contract_no  VARCHAR(255) DEFAULT NULL COMMENT '合同编号',
    auth_scope   INT          DEFAULT NULL COMMENT '授权范围（事件未携带，-1 表示未知）',
    max_mint     INT          DEFAULT NULL COMMENT '最大铸造数',
    product_hint VARCHAR(32)  DEFAULT NULL COMMENT '产品标识',
    block_number BIGINT       DEFAULT NULL COMMENT '登记区块',
    tx_hash      VARCHAR(66)  DEFAULT NULL COMMENT '登记交易',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (auth_id),
    KEY idx_auth_product (product_hint),
    KEY idx_auth_owner (ip_owner)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP 授权登记表';

-- 索引进度
CREATE TABLE IF NOT EXISTS eb_sync (
    k VARCHAR(64)  NOT NULL,
    v VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (k)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='索引同步状态';
