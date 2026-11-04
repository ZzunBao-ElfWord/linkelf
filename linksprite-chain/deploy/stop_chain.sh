#!/bin/bash
# 连接精灵链 - 停止脚本（systemd 调用）
pkill -f 'fisco-bcos -c config.ini' 2>/dev/null || true
pkill -f 'sleep infinity' 2>/dev/null || true
exit 0
