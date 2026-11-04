#!/bin/bash
# ============================================================
# 连接精灵链 (LinkSprite Chain) - 一键部署脚本
# 用法: ./deploy.sh {install|build|start|stop|status|balance|all}
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    echo "连接精灵链 - 部署管理工具"
    echo ""
    echo "用法: $0 {命令}"
    echo ""
    echo "命令:"
    echo "  install   安装 FISCO BCOS 环境"
    echo "  build     创建 4 节点联盟链"
    echo "  start     启动所有节点"
    echo "  stop      停止所有节点"
    echo "  status    检查节点状态"
    echo "  balance   开启 Balance (Gas) 功能"
    echo "  all       一键执行: install -> build -> start -> status"
    echo ""
}

if [ $# -eq 0 ]; then
    usage
    exit 0
fi

case "$1" in
    install)
        bash "${SCRIPT_DIR}/install_fisco.sh"
        ;;
    build)
        bash "${SCRIPT_DIR}/build_chain.sh"
        ;;
    start)
        bash "${SCRIPT_DIR}/start_all.sh"
        ;;
    stop)
        bash "${SCRIPT_DIR}/stop_all.sh"
        ;;
    status)
        bash "${SCRIPT_DIR}/check_status.sh"
        ;;
    balance)
        bash "${SCRIPT_DIR}/enable_balance.sh"
        ;;
    all)
        echo "=== 一键部署: 安装 -> 建链 -> 启动 -> 检查 ==="
        bash "${SCRIPT_DIR}/install_fisco.sh"
        bash "${SCRIPT_DIR}/build_chain.sh"
        bash "${SCRIPT_DIR}/start_all.sh"
        bash "${SCRIPT_DIR}/check_status.sh"
        echo ""
        echo "=== 部署完成！如需开启 Balance(Gas) 运行: $0 balance ==="
        ;;
    *)
        usage
        exit 1
        ;;
esac
