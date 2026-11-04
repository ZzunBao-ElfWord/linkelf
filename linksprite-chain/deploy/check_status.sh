#!/bin/bash
# ============================================================
# 连接精灵链 (LinkSprite Chain) - 节点状态检查
# 检查：进程状态、端口监听、区块高度、共识状态
# ============================================================

CHAIN_DIR="${HOME}/linksprite/nodes"
RPC_PORT_BASE=30300  # RPC端口起始（实际端口需与config.ini一致）

echo "============================================"
echo "  连接精灵链 - 节点状态检查"
echo "============================================"

if [ ! -d "${CHAIN_DIR}" ]; then
    echo "[错误] 链目录不存在"
    exit 1
fi

ALL_OK=true

for NODE_DIR in ${CHAIN_DIR}/node*; do
    NODE_NAME=$(basename "${NODE_DIR}")
    NODE_NUM=$(echo "${NODE_NAME}" | grep -oE '[0-9]+')

    echo ""
    echo "--- ${NODE_NAME} ---"

    # 1. 进程状态
    if [ -f "${NODE_DIR}/fisco-bcos.pid" ]; then
        PID=$(cat "${NODE_DIR}/fisco-bcos.pid" 2>/dev/null)
        if kill -0 "${PID}" 2>/dev/null; then
            echo "  进程: 运行中 (PID: ${PID})"
        else
            echo "  进程: 已停止 (PID 文件存在但进程不存在)"
            ALL_OK=false
            continue
        fi
    else
        echo "  进程: 未启动"
        ALL_OK=false
        continue
    fi

    # 2. 端口监听（检查实际监听端口，区分端口冲突与配置未生效）
    RPC_PORT=$(grep -E '^\s*listen_port' "${NODE_DIR}/config.ini" 2>/dev/null | head -1 | grep -oE '[0-9]+')
    if [ -z "${RPC_PORT}" ]; then
        RPC_PORT=$((RPC_PORT_BASE + NODE_NUM * 2))
    fi

    if command -v ss &> /dev/null; then
        LISTENING=$(ss -tlnp 2>/dev/null | grep ":${RPC_PORT} " | head -1)
    elif command -v netstat &> /dev/null; then
        LISTENING=$(netstat -tlnp 2>/dev/null | grep ":${RPC_PORT} " | head -1)
    else
        LISTENING=""
    fi

    if [ -n "${LISTENING}" ]; then
        echo "  端口: ${RPC_PORT} 监听中"
    else
        echo "  端口: ${RPC_PORT} 未监听 (检查config.ini是否被正确加载)"
        ALL_OK=false
    fi

    # 3. 区块高度（通过 RPC 查询）
    BLOCK_NUM=$(curl -s -X POST --data '{"jsonrpc":"2.0","method":"getBlockNumber","params":[1],"id":1}' http://127.0.0.1:${RPC_PORT} 2>/dev/null | grep -oE '"result":"[0-9x]+"' | head -1 | grep -oE '0x[0-9a-fA-F]+')
    if [ -n "${BLOCK_NUM}" ]; then
        echo "  区块高度: ${BLOCK_NUM}"
    else
        echo "  区块高度: 无法查询"
    fi

    # 4. 共识状态
    CONSENSUS=$(curl -s -X POST --data '{"jsonrpc":"2.0","method":"getConsensusStatus","params":[1],"id":1}' http://127.0.0.1:${RPC_PORT} 2>/dev/null | grep -oE '"viewNumber":"[0-9]+"' | head -1)
    if [ -n "${CONSENSUS}" ]; then
        echo "  共识视图: ${CONSENSUS}"
    fi
done

echo ""
echo "============================================"
if [ "${ALL_OK}" = true ]; then
    echo "  所有节点状态正常"
else
    echo "  部分节点异常，请检查上方日志"
fi
echo "============================================"
