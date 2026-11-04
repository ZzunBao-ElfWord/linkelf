#!/bin/bash
# ============================================================
# 连接精灵链 (LinkSprite Chain) - 智能合约部署脚本
# 部署顺序：KYC -> TransferPolicy -> AuthorizationRegistry -> LinkSpriteNFT
#          -> SpriteComputeToken -> AIAccountFactory -> Paymaster
#
# 依赖：FISCO BCOS Java 控制台 (console)
# 用法：./deploy_contracts.sh <console_path>
# ============================================================

set -e

CONSOLE_PATH="${1:-${HOME}/linksprite/console}"
CONTRACT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../contracts" && pwd)"

echo "============================================"
echo "  连接精灵链 - 智能合约部署"
echo "  合约目录: ${CONTRACT_DIR}"
echo "  控制台: ${CONSOLE_PATH}"
echo "============================================"

if [ ! -d "${CONSOLE_PATH}" ]; then
    echo "[错误] FISCO BCOS 控制台不存在: ${CONSOLE_PATH}"
    echo "请先部署控制台: https://fisco-bcos-doc.readthedocs.io/"
    exit 1
fi

# 平台账户地址（替换为实际平台账户）
PLATFORM_ADDR="0x0000000000000000000000000000000000000000"

echo ""
echo "平台账户: ${PLATFORM_ADDR}"
echo "（请将 PLATFORM_ADDR 替换为实际平台账户地址）"
echo ""

# 部署函数
deploy_contract() {
    local CONTRACT_FILE=$1
    local CONTRACT_NAME=$2
    local CONSTRUCTOR_ARGS=$3

    echo "[部署] ${CONTRACT_NAME}..."
    cd "${CONSOLE_PATH}"

    # 使用控制台部署（需先编译合约）
    # 实际部署命令根据控制台版本调整
    cat <<EOF
# 请在控制台中执行以下命令部署 ${CONTRACT_NAME}:

# 1. 编译合约（将 ${CONTRACT_FILE} 放入控制台 contracts 目录）
# cp ${CONTRACT_DIR}/${CONTRACT_FILE} ${CONSOLE_PATH}/contracts/

# 2. 部署
# deploy ${CONTRACT_NAME} ${CONSTRUCTOR_ARGS}

EOF
}

echo "============================================"
echo "  部署顺序与构造参数"
echo "============================================"
echo ""
echo "1. KYCRegistry"
echo "   构造参数: ${PLATFORM_ADDR}"
echo ""
echo "2. TransferPolicy"
echo "   构造参数: ${PLATFORM_ADDR}"
echo ""
echo "3. AuthorizationRegistry"
echo "   构造参数: ${PLATFORM_ADDR}"
echo ""
echo "4. LinkSpriteNFT"
echo "   构造参数: ${PLATFORM_ADDR}, <AuthorizationRegistry地址>, <TransferPolicy地址>, <KYCRegistry地址>"
echo ""
echo "5. SpriteComputeToken (SCT)"
echo "   构造参数: ${PLATFORM_ADDR}"
echo ""
echo "6. AIAccountFactory"
echo "   构造参数: ${PLATFORM_ADDR}, <SCT地址>"
echo ""
echo "7. Paymaster"
echo "   构造参数: ${PLATFORM_ADDR}, <SCT地址>"
echo ""
echo "============================================"
echo "  部署完成后需执行的初始化操作"
echo "============================================"
echo ""
echo "1. SCT 设置 AI 工厂地址:"
echo "   SpriteComputeToken.setAIFactory(<AIAccountFactory地址>)"
echo ""
echo "2. 给 Paymaster 充值 Native Balance (用于代付Gas):"
echo "   向 Paymaster 地址转账 Balance"
echo ""
echo "3. 创建首个 AI 账户并分配 SCT:"
echo "   AIAccountFactory.createAIAccount(<AI控制器地址>)"
echo "   SpriteComputeToken.mintToAI(<AI账户地址>, <数量>)"
echo ""
echo "4. 设置 Paymaster 操作定价（可选）:"
echo "   Paymaster.setOperationPrice(<函数选择器>, <SCT价格>)"
echo ""
