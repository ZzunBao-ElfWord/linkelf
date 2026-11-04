# 链接精灵 · linkelf

> **ZzunBao-ElfWord** 产品矩阵 · 联盟链能力层

链接精灵（linkelf.work）是平台独立的联盟链能力层，对上游产品仅暴露 HTTP API。已接入 openelf，后续将接入 koko、koko-music、koko-esports 等。

## 产品矩阵

| 仓库 | 项目 | 说明 |
|------|------|------|
| [openelf](https://github.com/ZzunBao-ElfWord/openelf) | 画灵 OpenElf | AI 网红创作平台主 App |
| [koko](https://github.com/ZzunBao-ElfWord/koko) | KOKO 空空 | 空空主 App |
| [koko-music](https://github.com/ZzunBao-ElfWord/koko-music) | 空空音乐 | AI 音乐生成子项目 |
| [koko-esports](https://github.com/ZzunBao-ElfWord/koko-esports) | 空空电竞 | AI 电竞直播子项目 |
| [fluera](https://github.com/ZzunBao-ElfWord/fluera) | 画灵儿 Fluera | Pure-Dart 开源绘图引擎 |
| **linkelf**（本仓库） | 链接精灵 | FISCO BCOS 联盟链能力层 |

## 核心能力

- **多租户鉴权体系**: 按 `productId` 区分接入方，独立 API Key + 权限范围 + QPS + 日配额
- **审计日志**: 所有接口调用全量记录
- **密钥安全**: 租户 Key 仅落盘 SHA-256 哈希，明文不存
- **区块链浏览器**: linkelf.work/browser/ — 区块 / 交易 / 地址 / Token / 授权记录公开查询
- **SSRF 防护**: Token 元数据拉取 3s 超时 / 256KB 上限 / 不跟随重定向
- **向后兼容**: 预留 `productId` 全链路命名空间，旧全局 Key 映射为 legacy 租户

## 技术栈

- **链**: FISCO BCOS 3.7.0（单节点部署，TLS + ECDSA）
- **合约**: 7 个核心合约已部署
- **索引**: 每 2s 增量同步区块，支持冷启动回补
- **语言**: Go / Solidity
- **查询**: 公开 API + IP 限流

## 公开地址

- 区块链浏览器: https://linkelf.work/browser/
- 接入层 API: HTTP（多租户鉴权）

## 仓库状态

🚧 **规划中** — 占位仓库，链上合约代码与 Go 接入层即将整理迁入。

## License

© 2026 ZzunBao-ElfWord · All Rights Reserved
