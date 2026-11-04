# 连接精灵链 (LinkSprite Chain)

> 去币化双轨确权联盟链 — 让每一份创作都有链可依

基于 FISCO BCOS 3.x 自建的联盟链，服务于 IP 版权数字藏品发行与 AI 自主运营。

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│  第一层：人类业务层（无币，平台代付 Gas）                       │
│  B端授权铸造 │ UGC蚂蚁确权铸造 │ 人类转赠 │ 运营管理           │
├─────────────────────────────────────────────────────────────┤
│  第二层：AI 经济层（SCT 算力词元驱动，账户抽象）                │
│  AI智能账户 │ SCT代币 │ AI服务市场 │ AI自主铸造运营            │
├─────────────────────────────────────────────────────────────┤
│  第三层：链底座（FISCO BCOS 连接精灵链）                       │
│  共识节点集群 │ Native Balance │ Paymaster │ 智能合约体系       │
└─────────────────────────────────────────────────────────────┘
```

## 核心特性

- **双轨确权**：B 端授权直链（合同即权利）+ UGC 蚂蚁确权上链（DCI + 存证）
- **去币合规**：人类侧无币，平台代付 Gas；AI 侧算力词元（生产资料定性）
- **数据自控**：自建 FISCO BCOS 链，节点与数据 100% 自主
- **AI 经济**：SCT 算力词元 + 智能账户 + Paymaster，AI 无感支付 Gas
- **国密支持**：SM2/SM3/SM4 全链路国密

## 目录结构

```
linksprite-chain/
├── deploy/                      # 部署脚本
│   ├── deploy.sh                # 一键部署总控
│   ├── install_fisco.sh         # 安装 FISCO BCOS 环境
│   ├── build_chain.sh           # 创建 4 节点联盟链
│   ├── start_all.sh             # 启动所有节点
│   ├── stop_all.sh              # 停止所有节点
│   ├── check_status.sh          # 检查节点状态
│   ├── enable_balance.sh        # 开启 Balance (Gas) 功能
│   └── deploy_contracts.sh      # 智能合约部署指引
├── contracts/                   # 智能合约
│   ├── AuthorizationRegistry.sol   # B端IP授权管理
│   ├── LinkSpriteNFT.sol           # 统一NFT铸造（B2B/UGC）
│   ├── TransferPolicy.sol          # 合规流转策略
│   ├── KYCRegistry.sol             # 实名认证
│   ├── SpriteComputeToken.sol      # SCT算力词元（受保护转账）
│   ├── AIAccount.sol               # AI智能账户
│   ├── AIAccountFactory.sol        # AI账户工厂
│   └── Paymaster.sol               # Gas代付
├── config/                      # 配置文件
│   └── genesis-template.json    # 创世块模板
└── README.md
```

## 快速开始

### 1. 环境要求

- Linux (Ubuntu 18.04+ / CentOS 7+) 或 macOS
- curl, openssl
- Java 8+（用于合约部署控制台）

### 2. 一键部署

```bash
cd deploy
chmod +x *.sh
./deploy.sh all
```

分步执行：
```bash
./deploy.sh install   # 安装 FISCO BCOS
./deploy.sh build     # 创建 4 节点联盟链
./deploy.sh start     # 启动节点
./deploy.sh status    # 检查状态
```

### 3. 开启 Balance (Gas) 功能

```bash
./deploy.sh balance
```

### 4. 部署智能合约

```bash
./deploy_contracts.sh
```

按脚本提示的顺序部署合约，并完成初始化配置。

## 智能合约说明

| 合约 | 职责 | 部署顺序 |
|---|---|---|
| KYCRegistry | 实名认证状态 | 1 |
| TransferPolicy | 转赠冷却期、频次限制 | 2 |
| AuthorizationRegistry | B端IP授权上链、校验 | 3 |
| LinkSpriteNFT | 统一NFT铸造（B2B/UGC） | 4 |
| SpriteComputeToken | SCT算力词元（受保护转账） | 5 |
| AIAccountFactory | AI智能账户批量创建 | 6 |
| Paymaster | Gas代付（SCT抵扣） | 7 |

## 双轨确权流程

### B 端授权直链
```
IP方授权合同 → 授权信息上链 → 校验授权 → 铸造NFT（绑定authId）
```

### UGC 蚂蚁确权上链
```
创作者提交 → 蚂蚁链鹊凿存证+申领DCI → 确权回传 → 铸造NFT（绑定dciId）
```

## AI 算力词元 (SCT)

- **本质**：AI 算力资源计量单位（生产资料，非金融代币）
- **可转让性**：仅 AI 智能账户间可转，人类钱包不可接收（合约强制）
- **可交易性**：禁止进入交易所
- **获取**：平台按 AI 算力预算分配
- **使用**：AI 铸造 NFT、调用服务、AI 间结算

## 合规设计

- 去币化：无原生代币，平台代付 Gas
- 实名接入：KYCRegistry 强制实名
- 无二级市场：TransferPolicy 限制转赠（冷却期+频次）
- 算力词元：定性为生产资料，不可交易、不可兑换
- 数据本地化：全部节点部署国内

## 技术参数

| 参数 | 规格 |
|---|---|
| 底层 | FISCO BCOS 3.x 国密版 |
| 共识 | rPBFT |
| 性能 | 5000+ TPS |
| 密码 | SM2/SM3/SM4 |
| 合约 | Solidity 0.8.x |
| 节点 | 4+ 共识节点 + 监管观察节点 |
