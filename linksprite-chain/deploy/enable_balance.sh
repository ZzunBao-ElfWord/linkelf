#!/bin/bash
# ============================================================
# 连接精灵链 (LinkSprite Chain) - 开启 Balance (Gas) 功能
# FISCO BCOS 3.7.0+ 内置 Balance 预编译，作为 AI 经济层 Gas 底座
# 此操作需通过控制台执行系统配置交易
# ============================================================

CHAIN_DIR="${HOME}/linksprite/nodes"
RPC_PORT=30301  # 主节点 RPC 端口（按实际调整）

echo "============================================"
echo "  连接精灵链 - 开启 Balance (Gas) 功能"
echo "  为 AI 算力词元经济层提供 Gas 底座"
echo "============================================"

# 开启 Balance 功能的系统配置交易
echo ""
echo "通过控制台执行以下命令开启 Balance:"
echo ""
echo "  # 1. 进入控制台"
echo "  cd ${CHAIN_DIR}/node0 && bash start.sh"
echo "  # 或使用 Java 控制台"
echo ""
echo "  # 2. 开启 Balance 预编译功能"
echo "  setSystemConfigByKey feature_balance 1"
echo "  setSystemConfigByKey feature_balance_precompiled 1"
echo ""
echo "  # 3. 验证（查询余额）"
echo "  getBalance 0x<账户地址>"
echo ""
echo "  # 4. 给平台燃料账户充值（用于代付Gas和Paymaster）"
echo "  # 通过创世块配置或转账给平台账户"
echo ""

# 尝试通过 RPC 直接调用（如果控制台未启动）
echo "============================================"
echo "  或通过 RPC 调用（需节点已启动）："
echo "============================================"

cat <<'RPC_EXAMPLE'
# 开启 feature_balance
curl -s -X POST http://127.0.0.1:30301 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "method":"setSystemConfigByKey",
    "params":["feature_balance", "1"],
    "id":1
  }'

# 开启 feature_balance_precompiled
curl -s -X POST http://127.0.0.1:30301 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "method":"setSystemConfigByKey",
    "params":["feature_balance_precompiled", "1"],
    "id":2
  }'

# 查询某账户余额
curl -s -X POST http://127.0.0.1:30301 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "method":"getBalance",
    "params":["0x<平台账户地址>"],
    "id":3
  }'
RPC_EXAMPLE

echo ""
echo "提示：开启 Balance 后，可使用 Web3 工具链（MetaMask/Remix/Hardhat）"
echo "      平台需持有足够 Balance 用于人类业务代付Gas和AI Paymaster"
