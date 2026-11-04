# linkelf · 链接精灵

> **ZzunBao-ElfWord** 产品矩阵 · 联盟链能力层

链接精灵（linkelf.work）是平台独立的联盟链能力层，对上游产品仅暴露 HTTP API。已接入 **openelf 开放精灵**，后续将接入 koko、kongkongmusic、kongkonggames 等。

## 产品矩阵

| 仓库 | 中文名 | 定位 | 状态 |
|------|--------|------|------|
| [openelf](https://github.com/ZzunBao-ElfWord/OpenElf) | 开放精灵 | AI 网红创作平台主 App · Flutter | ✅ 主开发 |
| [hualing](https://github.com/ZzunBao-ElfWord/hualing) | 画灵儿 | Pure-Dart 开源绘图引擎 | ✅ 开源已发布 |
| [koko](https://github.com/ZzunBao-ElfWord/koko) | 空空 | kokokongkong.com · Flutter + Admin Web + NFT 生成器 | ✅ 完整仓库 |
| [kongkongmusic](https://github.com/ZzunBao-ElfWord/kongkongmusic) | 空空音乐 | AI 音乐生成子项目 | 🚧 规划中 |
| [kongkonggames](https://github.com/ZzunBao-ElfWord/kongkonggames) | 空空电竞 | AI 电竞直播子项目 | 🚧 规划中 |
| [**linkelf**](https://github.com/ZzunBao-ElfWord/linkelf) | 链接精灵 | FISCO BCOS 联盟链能力层 | ✅ 已上线 |

## 核心能力

- **多租户鉴权**: 按 `productId` 区分接入方，独立 API Key + 权限范围 + QPS + 日配额
- **审计日志**: 所有接口调用全量记录
- **密钥安全**: 租户 Key 仅落盘 SHA-256 哈希，明文不存；运维通过 `X-Ops-Token` 操作
- **区块链浏览器**: linkelf.work/browser/ — 区块 / 交易 / 地址 / Token / 授权记录公开查询
- **SSRF 防护**: Token 元数据拉取 3s 超时 / 256KB 上限 / 不跟随重定向
- **向后兼容**: 预留 `productId` 全链路命名空间，旧全局 Key 映射为 legacy 租户
- **沙箱自助申请**: linkelf.work/sandbox/ — 自助申请免费测试租户，3 QPS / 500 次日配额 / 30 天到期自动吊销

## 技术栈

| 层 | 技术 | 说明 |
|----|------|------|
| 链 | FISCO BCOS 3.7.0 | 单节点 · TLS · ECDSA |
| 合约 | Solidity 7 个核心合约 | AIAccountFactory · KYCRegistry · LinkSpriteNFT · AuthorizationRegistry · Paymaster · SpriteComputeToken · TransferPolicy |
| 后端 | Java 17 + Spring Boot 3 | 联盟链核心服务 + explorer 索引器 + 多租户鉴权（linksprite-backend/） |
| 沙箱 | Node 20 零依赖 | 租户自助申请服务（linkelf-sandbox/） |
| 查询 | 公开 API + IP 限流 | ExplorerIndexer 每 2s 增量同步 |
| 前端 | 原生 HTML/JS/CSS | linkelf.work 官网 + 浏览器前端 + 沙箱页面 |
| 部署 | Docker Compose + systemd | FISCO BCOS 节点 + 后端 + 监控 |

## 仓库结构

```
linkelf/
├── README.md
├── LICENSE
├── .gitignore
├── linkelf-site/              # 🌐 linkelf.work 官网 + 浏览器 + 沙箱前端
│   ├── index.html             # 官网首页
│   ├── main.js                # 官网交互
│   ├── styles.css             # 官网样式
│   ├── favicon.svg
│   ├── wechat-qr.jpg          # 商务微信二维码
│   ├── serve.py               # 本地静态服务器（Python 3，零依赖）
│   ├── browser/               # 📜 LinkSprite Explorer · 轻量区块链浏览器
│   │   ├── index.html         # 5 路由：概览 / 区块 / 交易 / Token / 授权
│   │   ├── app.js             # 浏览器前端（搜索 + 分页 + API 调用）
│   │   └── app.css
│   ├── sandbox/               # 🧪 沙箱自助申请页（前端）
│   │   ├── index.html
│   │   ├── sandbox.css
│   │   └── sandbox.js
│   └── deploy/
│       └── linkelf.work.conf # nginx 部署配置
├── linksprite-backend/        # ☕ Java 17 + Spring Boot 3 · 联盟链后端（114 files）
│   ├── pom.xml                # Maven 项目配置
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── deploy/                # 部署脚本 + schema.sql + e2e-check
│   ├── explorer-web/          # 📜 浏览器前端备选版本（和 linkelf-site/browser/ 同源）
│   └── src/main/java/com/linksprite/
│       ├── LinkSpriteApplication.java
│       ├── common/            # ApiKeyFilter / GlobalExceptionHandler / Result
│       ├── config/            # FiscoBcosConfig / ContractConfig / ExplorerConfig
│       ├── contract/          # 7 合约 Java 封装（AIAccountFactory / KYCRegistry / LinkSpriteNFT / AuthorizationRegistry / Paymaster / SpriteComputeToken / TransferPolicy）
│       ├── controller/        # REST API（Ai / Nft / Kyc / Authorization / Wallet / TenantAdmin / Deploy / Health / WorkSubmission）
│       ├── explorer/          # 🔍 ExplorerIndexer + ExplorerRepository + ExplorerController（每 2s 增量同步）
│       ├── service/           # 业务服务层（Nft / Kyc / Authorization / Wallet / ChainStatus / ContractDeployment / AntCopyright ...）
│       ├── tenant/            # 🛡️ 多租户鉴权核心（Tenant / TenantStore / RateLimiter / ScopeResolver）
│       └── resources/
│           ├── application.yml
│           └── explorer/     # schema.sql + upgrade.sql
├── linksprite-chain/          # ⛓️ FISCO BCOS 链合约 + 部署脚本（30 files）
│   ├── README.md
│   ├── config/genesis-template.json
│   ├── contracts/             # 7 个 Solidity 合约源码
│   │   ├── AIAccount.sol
│   │   ├── AIAccountFactory.sol
│   │   ├── KYCRegistry.sol
│   │   ├── LinkSpriteNFT.sol
│   │   ├── AuthorizationRegistry.sol
│   │   ├── Paymaster.sol
│   │   ├── SpriteComputeToken.sol
│   │   └── TransferPolicy.sol
│   └── deploy/                # 链部署运维脚本
│       ├── install_fisco.sh
│       ├── compile_contracts.sh
│       ├── deploy_contracts.sh
│       ├── start_chain.sh / stop_chain.sh
│       ├── linksprite-chain.service
│       └── linksprite-backend.service
├── linkelf-sandbox/           # 🧪 沙箱后端服务（Node 20，内置 http 模块）
│   ├── server.js              # 租户自助申请 + 到期扫描 + API Key 下发
│   └── linksprite-sandbox.service  # systemd unit
└── linkelf-biz/               # 💼 商务资料
    └── LinkElf-企业接入商务方案.md  # V1.0 完整商务方案
```

## 公开地址

| 服务 | URL |
|------|-----|
| 官网 | https://linkelf.work |
| 区块链浏览器 | https://linkelf.work/browser/ |
| 沙箱自助申请 | https://linkelf.work/sandbox/ |
| 接入层 API | HTTP（多租户鉴权） |

## 沙箱服务

沙箱是自助申请免费测试租户的 Node 20 服务，**零第三方依赖**（仅使用内置 http/fs/crypto）。

```bash
# 环境变量
SANDBOX_PORT=8090                    # 监听端口，默认 8090
SANDBOX_BACKEND=http://127.0.0.1:8080/api  # 后端租户接口
LINKSPRITE_OPS_TOKEN=xxx             # 运维 Token（必填）
SANDBOX_DIR=/opt/linksprite/sandbox  # 数据目录
SANDBOX_TTL_DAYS=30                  # 沙箱有效期
SANDBOX_QPS=3                        # QPS 上限
SANDBOX_DAILY_QUOTA=500              # 日配额
SANDBOX_MAX_PER_IP_DAY=3             # 同 IP 日申请上限

# 启动
node linkelf-sandbox/server.js
```

## 核心合约（7 个，已部署运行）

| 合约 | 职责 |
|------|------|
| AIAccountFactory | AI 身份注册与 KYC 认证 |
| KYCRegistry | KYC 认证记录与验证 |
| LinkSpriteNFT | AI 形象 / 数字人确权登记 |
| AuthorizationRegistry | 授权登记 / 范围 / 期限 / 撤回 |
| Paymaster | 算力 / 额度预付账本与结算 |
| SpriteComputeToken | 算力资源 Token 化登记 |
| TransferPolicy | 转账策略与风控规则 |

## License

© 2026 ZzunBao-ElfWord · All Rights Reserved
