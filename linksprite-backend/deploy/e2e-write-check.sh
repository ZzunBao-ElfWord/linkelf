#!/usr/bin/env bash
PORT=$(grep '^PORT=' /opt/koko-admin/.env | cut -d= -f2)
U=$(grep '^BOOTSTRAP_ADMIN_USERNAME=' /opt/koko-admin/.env | cut -d= -f2)
P=$(grep '^BOOTSTRAP_ADMIN_PASSWORD=' /opt/koko-admin/.env | cut -d= -f2)
T=$(curl -s -X POST localhost:$PORT/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"password\":\"$P\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
echo "== 真实写接口 POST /chain/kyc/verify（预期：链未启用业务错误）=="
curl -s -X POST "localhost:$PORT/api/v1/chain/kyc/verify?user=0xe2e-test&proofHash=0xtest" -H "Authorization: Bearer $T"; echo
rm -f /tmp/e2e.sh
echo "== 内存与容器 =="
free -m | head -2
docker ps --format '{{.Names}} {{.Status}}' | grep -E 'linksprite|koko-admin'
