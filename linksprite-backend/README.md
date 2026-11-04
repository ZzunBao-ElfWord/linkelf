# 连接精灵链 - Java SDK 后端服务

对接连接精灵链智能合约的 Spring Boot 后端服务框架。

## 技术栈

- JDK 17
- Spring Boot 3.2.5
- FISCO BCOS Java SDK 3.8.0
- MyBatis-Plus 3.5.5
- MySQL 8.0
- Redis 7.0
- Hutool / FastJSON2

## 项目结构

```
linksprite-backend/
├── pom.xml
└── src/main/
    ├── java/com/linksprite/
    │   ├── LinkSpriteApplication.java       # 启动类
    │   ├── config/                          # 配置类
    │   │   ├── FiscoBcosConfig.java         # 链连接参数配置
    │   │   ├── FiscoBcosClientConfig.java   # SDK 初始化
    │   │   └── ContractConfig.java          # 合约地址配置
    │   ├── contract/                        # 合约封装层
    │   │   ├── BaseContract.java            # 合约基类
    │   │   ├── ContractFactory.java         # 合约Bean工厂
    │   │   ├── AuthorizationRegistry.java   # 授权管理
    │   │   ├── LinkSpriteNFT.java           # NFT铸造
    │   │   ├── TransferPolicy.java          # 流转策略
    │   │   ├── KYCRegistry.java             # 实名认证
    │   │   ├── SpriteComputeToken.java      # SCT算力词元
    │   │   ├── AIAccountFactory.java        # AI账户工厂
    │   │   └── Paymaster.java               # Gas代付
    │   ├── service/                         # 业务服务层
    │   │   ├── BaseService.java
    │   │   ├── AuthorizationService.java
    │   │   ├── NftService.java
    │   │   ├── TransferService.java
    │   │   ├── KycService.java
    │   │   ├── SctService.java
    │   │   ├── AiService.java
    │   │   └── AntCopyrightService.java     # 蚂蚁链确权
    │   ├── controller/                      # REST API
    │   │   ├── AuthorizationController.java
    │   │   ├── NftController.java
    │   │   ├── KycController.java
    │   │   ├── SctController.java
    │   │   └── AiController.java
    │   ├── dto/
    │   │   ├── request/                     # 请求DTO
    │   │   └── response/                    # 响应DTO
    │   └── common/                          # 公共类
    │       ├── Result.java
    │       ├── ChainException.java
    │       └── GlobalExceptionHandler.java
    └── resources/
        ├── application.yml
        └── cert/                            # FISCO BCOS 证书
```

## 快速开始

### 1. 准备证书

将 FISCO BCOS 节点的 SDK 证书放入 `src/main/resources/cert/` 目录：
- `ca.crt`、`sdk.crt`、`sdk.key`、`sdk.pem`

详见 [cert/README.md](src/main/resources/cert/README.md)

### 2. 配置节点与合约地址

编辑 `src/main/resources/application.yml`：

```yaml
fisco:
  crypto-type: SM              # 国密或 ECDSA，需与节点一致
  peers:
    - 127.0.0.1:20200         # 节点 ChannelPort
  group-id: group0
  account:
    account-file: classpath:cert/sdk.pem

contract:
  authorization-registry: "0x..."   # 填入部署后的合约地址
  linksprite-nft: "0x..."
  # ...
```

### 3. 替换合约 ABI

合约封装类中的 `ABI` 常量需要替换为编译后的实际 ABI：

```bash
# 方式1：使用 FISCO BCOS console 生成
sol2java -a AuthorizationRegistry.abi -p com.linksprite.contract

# 方式2：从编译输出复制 ABI 到对应合约类的 ABI 常量
```

### 4. 启动服务

```bash
mvn clean package -DskipTests
java -jar target/linksprite-backend-1.0.0.jar
```

服务启动后访问：http://localhost:8080/api

## API 接口

### 授权管理
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/register` | 登记B端IP授权 |
| POST | `/auth/revoke/{authId}` | 撤销授权 |
| GET | `/auth/valid/{authId}` | 校验授权有效性 |
| GET | `/auth/{authId}` | 查询授权详情 |

### NFT 铸造
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/nft/mint/b2b` | B端铸造（绑定授权） |
| POST | `/nft/mint/ugc` | UGC铸造（绑定DCI） |
| POST | `/nft/transfer` | 转赠NFT |
| GET | `/nft/owner/{tokenId}` | 查询所有者 |
| GET | `/nft/balance/{owner}` | 查询持有数量 |
| GET | `/nft/proof/{tokenId}` | 查询权利证明 |

### 实名认证
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/kyc/verify` | 实名认证 |
| GET | `/kyc/verified/{user}` | 查询实名状态 |

### SCT 算力词元
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/sct/mint` | 铸造SCT给AI |
| GET | `/sct/balance/{account}` | 查询SCT余额 |

### AI 账户
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/ai/account/create` | 创建AI账户 |
| POST | `/ai/sct/allocate` | 分配SCT预算 |
| POST | `/ai/execute` | AI执行操作（Paymaster代付Gas） |

## 统一返回格式

所有接口返回统一结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "txHash": "0x...",
    "blockNumber": "1234",
    "status": "0x0",
    "message": ""
  }
}
```

写操作返回 `ChainTxResult`，包含 `txHash` 和 `blockNumber`，可用于区块链浏览器查询。

## 关键设计

1. **配置集中化**：所有链连接参数和合约地址集中在配置类，从 application.yml 读取
2. **交易结果透明**：所有写操作返回 txHash + blockNumber，便于链上验证
3. **类型转换**：Controller 层将 SDK 返回的 BigInteger 等类型转换为前端友好格式
4. **异常统一**：链上异常通过 ChainException 抛出，全局处理器统一返回

## 注意事项

1. 合约 ABI 必须替换为编译后的实际值，否则调用会失败
2. 平台账户（sdk.pem）需在创世块中预分配 Balance
3. 合约部署后需将地址填入 application.yml
4. 生产环境建议使用异步上链（消息队列）避免阻塞
