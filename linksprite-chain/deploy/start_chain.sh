#!/bin/bash
# 连接精灵链 - 启动脚本（systemd 调用，后台 daemonize 节点进程并挂载到服务 cgroup）
# 绕过 start_all.sh 的进程检测竞争问题
CHAIN_DIR="/root/linksprite/nodes/127.0.0.1"
cd "${CHAIN_DIR}"

for i in 0 1 2 3; do
    if [ -d "node${i}" ]; then
        (cd "node${i}" && nohup ../fisco-bcos -c config.ini -g config.genesis >> nohup.out 2>&1 &)
        echo "started node${i}"
    fi
done
# 保持前台等待，systemd Type=forking 会因主进程退出而标记 inactive；
# 用 sleep infinity 让服务保持 active，节点进程归入本服务 cgroup 一并管理
sleep infinity &
wait $!
