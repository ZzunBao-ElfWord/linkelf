#!/bin/bash
# ============================================================
# 连接精灵链 (LinkSprite Chain) - 停止所有节点
# ============================================================

set -e

CHAIN_DIR="${HOME}/linksprite/nodes"

echo "============================================"
echo "  连接精灵链 - 停止所有节点"
echo "============================================"

if [ ! -d "${CHAIN_DIR}" ]; then
    echo "[错误] 链目录不存在"
    exit 1
fi

for NODE_DIR in ${CHAIN_DIR}/node*; do
    NODE_NAME=$(basename "${NODE_DIR}")
    echo "[停止] ${NODE_NAME}..."

    cd "${NODE_DIR}"
    if [ -x "./stop.sh" ]; then
        bash ./stop.sh
        echo "  [OK] ${NODE_NAME} 已停止"
    else
        # 兜底：通过 PID 杀进程
        if [ -f "fisco-bcos.pid" ]; then
            PID=$(cat fisco-bcos.pid 2>/dev/null)
            if [ -n "${PID}" ] && kill -0 "${PID}" 2>/dev/null; then
                kill "${PID}"
                echo "  [OK] ${NODE_NAME} 已停止 (PID: ${PID})"
            else
                echo "  [跳过] ${NODE_NAME} 未运行"
            fi
        else
            echo "  [跳过] ${NODE_NAME} 无 PID 文件"
        fi
    fi
done

echo ""
echo "所有节点已停止"
