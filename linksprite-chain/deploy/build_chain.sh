#!/bin/bash
# ============================================================
# 连接精灵链 (LinkSprite Chain) - 建链脚本
# 创建 4 节点 FISCO BCOS 3.x Air 版本联盟链
# 启用国密算法，开启群组架构
# ============================================================

set -e

WORK_DIR="${HOME}/linksprite"
CHAIN_DIR="${WORK_DIR}/nodes"
NODE_COUNT=4
CHAIN_ID="linksprite_chain"
GROUP_ID="group0"
IP_LIST="127.0.0.1"  # 生产环境替换为实际节点IP，多个用逗号分隔

echo "============================================"
echo "  连接精灵链 - 建链"
echo "  节点数: ${NODE_COUNT}"
echo "  链ID: ${CHAIN_ID}"
echo "  群组: ${GROUP_ID}"
echo "============================================"

cd "${WORK_DIR}"

if [ ! -f "build_chain.sh" ]; then
    echo "[错误] build_chain.sh 不存在，请先运行 install_fisco.sh"
    exit 1
fi

# 清理旧链（可选）
if [ -d "${CHAIN_DIR}" ]; then
    read -p "检测到已有链目录，是否删除重建？(y/N): " confirm
    if [ "${confirm}" = "y" ] || [ "${confirm}" = "Y" ]; then
        echo "  删除旧链目录..."
        rm -rf "${CHAIN_DIR}"
    else
        echo "  保留旧链，退出"
        exit 0
    fi
fi

# 建链
# -p: 端口起始
# -v: 版本
# -g: 国密
# -n: 节点数
# -i: IP列表
echo "[1/2] 执行建链..."
bash build_chain.sh \
    -p 30300 \
    -v 3.7.0 \
    -g \
    -n ${NODE_COUNT} \
    -i "${IP_LIST}" \
    -d "${CHAIN_DIR}"

echo ""
echo "[2/2] 生成链信息..."
if [ -d "${CHAIN_DIR}" ]; then
    echo "============================================"
    echo "  建链成功！"
    echo "  链目录: ${CHAIN_DIR}"
    echo "  节点列表:"
    for i in $(seq 0 $((NODE_COUNT - 1))); do
        echo "    node${i}: 127.0.0.1:$((30300 + i * 2))"
    done
    echo ""
    echo "  下一步: 运行 start_all.sh 启动节点"
    echo "============================================"
else
    echo "  [错误] 建链失败，请检查输出"
    exit 1
fi
