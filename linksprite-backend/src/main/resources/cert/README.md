# 将 FISCO BCOS 节点的 SDK 证书放入此目录

## 所需文件

从 FISCO BCOS 节点目录复制以下证书到本目录：

```
cert/
├── ca.crt        # 链 CA 证书
├── sdk.crt       # SDK 证书
├── sdk.key       # SDK 私钥
└── sdk.pem       # SDK 账户文件（PEM 格式，含私钥）
```

## 获取方式

在 FISCO BCOS 节点部署目录中：

```bash
# 国密版本
cp nodes/127.0.0.1/sdk/ca.crt src/main/resources/cert/
cp nodes/127.0.0.1/sdk/sdk.crt src/main/resources/cert/
cp nodes/127.0.0.1/sdk/sdk.key src/main/resources/cert/
cp nodes/127.0.0.1/sdk/sdk.pem src/main/resources/cert/
```

## 注意事项

1. 证书类型必须与 `application.yml` 中的 `fisco.crypto-type` 一致
   - `SM` → 使用国密证书
   - `ECDSA` → 使用普通证书
2. `sdk.pem` 是平台账户的私钥文件，用于签名交易
3. 平台账户需在创世块中预分配 Balance（用于代付 Gas）
4. 切勿将证书提交到公开代码仓库
