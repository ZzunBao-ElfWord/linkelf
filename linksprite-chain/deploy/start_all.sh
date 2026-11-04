#!/bin/bash
# ============================================================
# 连接精灵链 (LinkSprite Chain) - 启动所有节点
# 节流启动：节点间间隔 2 秒，避免端口/资源竞争
# ============================================================

set -e

CHAIN_DIR="${HOME}/linksprite/nodes"
START_INTERVAL=2  # 节点启动间隔（秒）

echo "============================================"
echo "  连接精灵链 - 启动所有节点"
echo "  启动间隔: ${START_INTERVAL}s"
echo "============================================"

if [ ! -d "${CHAIN_DIR}" ]; then
    echo "[错误] 链目录不存在，请先运行 build_chain.sh"
    exit 1
fi

# 统计节点数
NODE_COUNT=$(ls -d ${CHAIN_DIR}/node* 2>/dev/null | wc -l)
if [ "${NODE_COUNT}" -eq 0 ]; then
    echo "[错误] 未找到节点目录"
    exit 1
fi

echo "发现 ${NODE_COUNT} 个节点，开始启动..."

INDEX=0
for NODE_DIR in ${CHAIN_DIR}/node*; do
    NODE_NAME=$(basename "${NODE_DIR}")
    echo ""
    echo "[启动] ${NODE_NAME}..."

    cd "${NODE_DIR}"

    # 检查是否已运行
    if [ -f "fisco-bcos.pid" ]; then
        PID=$(cat fisco-bcos.pid 2>/dev/null)
        if kill -0 "${PID}" 2>/dev/null; then
            echo "  [跳过] ${NODE_NAME} 已在运行 (PID: ${PID})"
            INDEX=$((INDEX + 1))
            continue
        fi
    fi

    # 启动节点，使用绝对路径确保配置正确加载
    if [ -x "./start.sh" ]; then
        bash ./start.sh
        sleep 1
        # 验证进程
        if [ -f "fisco-bcos.pid" ]; then
            PID=$(cat fisco-bcos.pid)
            if kill -0 "${PID}" 2>/dev/null; then
                echo "  [OK] ${NODE_NAME} 启动成功 (PID: ${PID})"
            else
                echo "  [警告] ${NODE_NAME} 进程未检测到，检查日志: ${NODE_DIR}/log/fisco.log"
            fi
        else
            echo "  [警告] ${NODE_NAME} 未生成 PID 文件"
        fi
    else
        echo "  [错误] ${NODE_NAME} 缺少 start.sh"
    fi

    INDEX=$((INDEX + 1))
    # 节流：最后一个节点不等待
    if [ "${INDEX}" -lt "${NODE_COUNT}" ]; then
        sleep "${START_INTERVAL}"
    fi
done

echo ""
echo "============================================"
echo "  启动命令执行完毕"
echo "  运行 check_status.sh 查看节点状态"
echo "============================================"
