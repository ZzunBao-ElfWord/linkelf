#!/bin/bash
# ============================================================
# 连接精灵链 (LinkSprite Chain) - FISCO BCOS 环境安装脚本
# 适用于 Linux (Ubuntu 18.04+/CentOS 7+) / macOS
# 安装 FISCO BCOS 3.x Air 版本 + 依赖
# ============================================================

set -e

FISCO_VERSION="3.7.0"
WORK_DIR="${HOME}/linksprite"
DOWNLOAD_URL="https://github.com/FISCO-BCOS/FISCO-BCOS/releases/download/v${FISCO_VERSION}/build_chain.sh"

echo "============================================"
echo "  连接精灵链 - FISCO BCOS 环境安装"
echo "  版本: ${FISCO_VERSION}"
echo "============================================"

# 1. 检查依赖
echo "[1/4] 检查系统依赖..."
check_dep() {
    if ! command -v $1 &> /dev/null; then
        echo "  [缺失] $1，尝试安装..."
        if [ -f /etc/os-release ]; then
            . /etc/os-release
            case $ID in
                ubuntu|debian) sudo apt-get install -y $1 ;;
                centos|rhel) sudo yum install -y $1 ;;
                *) echo "  [警告] 请手动安装 $1" ;;
            esac
        fi
    else
        echo "  [OK] $1"
    fi
}

check_dep curl
check_dep openssl

# 2. 创建工作目录
echo "[2/4] 创建工作目录: ${WORK_DIR}"
mkdir -p "${WORK_DIR}"
cd "${WORK_DIR}"

# 3. 下载 build_chain.sh
echo "[3/4] 下载 build_chain.sh (v${FISCO_VERSION})..."
if [ ! -f "build_chain.sh" ]; then
    curl -#LO "${DOWNLOAD_URL}" && chmod +x build_chain.sh
    echo "  下载完成"
else
    echo "  build_chain.sh 已存在，跳过下载"
fi

# 4. 验证
echo "[4/4] 验证安装..."
if [ -f "build_chain.sh" ] && [ -x "build_chain.sh" ]; then
    echo "============================================"
    echo "  安装成功！"
    echo "  工作目录: ${WORK_DIR}"
    echo "  下一步: 运行 ./build_chain.sh 建链"
    echo "============================================"
else
    echo "  [错误] 安装失败，请检查网络连接"
    exit 1
fi
