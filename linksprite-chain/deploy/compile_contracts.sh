#!/bin/bash
# ============================================================
# 连接精灵链 - Solidity 合约编译流水线 (Linux / macOS / WSL)
#
# 1. 下载 solc 0.8.20（与 pragma ^0.8.20 对齐）
# 2. 编译 linksprite-chain/contracts 下全部合约，输出 ABI + BIN
# 3. 拷贝产物到后端 linksprite-backend/src/main/resources/contracts
# 4. 运行 ABI 一致性校验（与 Java 内嵌 ABI 比对）
#
# 用法：bash deploy/compile_contracts.sh
# ============================================================
set -euo pipefail

SOLC_VERSION="0.8.20"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHAIN_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CONTRACT_DIR="${CHAIN_DIR}/contracts"
TOOLS_DIR="${CHAIN_DIR}/tools"
OUT_DIR="${CHAIN_DIR}/build/contracts"
BACKEND_DIR="$(cd "${CHAIN_DIR}/../linksprite-backend" && pwd)"
RESOURCE_CONTRACTS_DIR="${BACKEND_DIR}/src/main/resources/contracts"

# 与 Java 侧 ContractAbis.DEPLOY_ORDER 保持一致（AIAccount 由工厂内嵌，一并编译）
TARGET_CONTRACTS=(
  "KYCRegistry" "TransferPolicy" "AuthorizationRegistry" "LinkSpriteNFT"
  "SpriteComputeToken" "AIAccountFactory" "Paymaster" "AIAccount"
)

OS="$(uname -s)"
case "${OS}" in
  Linux*)  SOLC_BIN="${TOOLS_DIR}/solc-${SOLC_VERSION}"; SOLC_URL="https://github.com/ethereum/solidity/releases/download/v${SOLC_VERSION}/solc-static-linux" ;;
  Darwin*) SOLC_BIN="${TOOLS_DIR}/solc-macos-${SOLC_VERSION}"; SOLC_URL="https://github.com/ethereum/solidity/releases/download/v${SOLC_VERSION}/solc-macos" ;;
  *) echo "[错误] 不支持的系统: ${OS}，Windows 请使用 compile_contracts.ps1"; exit 1 ;;
esac

mkdir -p "${TOOLS_DIR}" "${OUT_DIR}" "${RESOURCE_CONTRACTS_DIR}"

# 1. 获取 solc
if [ ! -x "${SOLC_BIN}" ]; then
  echo "[1/4] 下载 solc ${SOLC_VERSION} ..."
  echo "      ${SOLC_URL}"
  curl -L --fail --retry 3 -o "${SOLC_BIN}" "${SOLC_URL}"
  chmod +x "${SOLC_BIN}"
else
  echo "[1/4] 使用已存在的 solc: ${SOLC_BIN}"
fi
"${SOLC_BIN}" --version | head -n 2

# 2. 编译全部合约
echo "[2/4] 编译合约..."
"${SOLC_BIN}" \
  --base-path "${CONTRACT_DIR}" \
  --allow-paths "${CONTRACT_DIR}" \
  --optimize --optimize-runs 200 \
  --abi --bin \
  -o "${OUT_DIR}" --overwrite \
  "${CONTRACT_DIR}"/*.sol

# 3. 拷贝目标产物到后端资源目录
echo "[3/4] 拷贝 ABI/BIN 到后端资源目录: ${RESOURCE_CONTRACTS_DIR}"
MISSING=0
for name in "${TARGET_CONTRACTS[@]}"; do
  if [ ! -f "${OUT_DIR}/${name}.bin" ] || [ ! -f "${OUT_DIR}/${name}.abi" ]; then
    echo "  [缺失] ${name}.abi/.bin"
    MISSING=1
    continue
  fi
  cp "${OUT_DIR}/${name}.bin" "${RESOURCE_CONTRACTS_DIR}/${name}.bin"
  cp "${OUT_DIR}/${name}.abi" "${RESOURCE_CONTRACTS_DIR}/${name}.abi"
  echo "  [就绪] ${name}"
done
if [ "${MISSING}" -ne 0 ]; then
  echo "[错误] 部分合约产物缺失，请检查编译输出"
  exit 1
fi

# 4. ABI 一致性校验
echo "[4/4] ABI 一致性校验..."
MVN_CMD="mvn"
if ! command -v mvn >/dev/null 2>&1; then
  if [ -x "${HOME}/tools/apache-maven-3.9.6/bin/mvn" ]; then
    MVN_CMD="${HOME}/tools/apache-maven-3.9.6/bin/mvn"
  else
    echo "[错误] 未找到 mvn，请安装 Maven 或设置 PATH"
    exit 1
  fi
fi
cd "${BACKEND_DIR}"
SETTINGS_ARG=""
if [ -f "${BACKEND_DIR}/settings.xml" ]; then
  SETTINGS_ARG="-s ${BACKEND_DIR}/settings.xml"
fi
${MVN_CMD} ${SETTINGS_ARG} -q compile exec:java \
  -Dexec.mainClass=com.linksprite.tools.AbiConsistencyChecker \
  -Dexec.classpathScope=compile

echo ""
echo "完成。产物目录: ${RESOURCE_CONTRACTS_DIR}"
echo "部署合约：启动后端（fisco.enabled=true）后 POST /api/deploy/contracts"
