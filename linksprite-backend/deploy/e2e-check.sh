#!/usr/bin/env bash
# koko-admin BFF -> linksprite-backend 端到端验证
set -e
ENV=/opt/koko-admin/.env
PORT=$(grep '^PORT=' $ENV | cut -d= -f2)
U=$(grep '^BOOTSTRAP_ADMIN_USERNAME=' $ENV | cut -d= -f2)
P=$(grep '^BOOTSTRAP_ADMIN_PASSWORD=' $ENV | cut -d= -f2)

echo "== 1. 登录 =="
LOGIN=$(curl -s -X POST localhost:$PORT/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"password\":\"$P\"}")
echo "$LOGIN" | head -c 200; echo
TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])" 2>/dev/null \
  || echo "$LOGIN" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
[ -n "$TOKEN" ] && echo "TOKEN OK (${#TOKEN} chars)"

echo "== 2. 代理链状态 GET /api/v1/chain/health =="
curl -s localhost:$PORT/api/v1/chain/health -H "Authorization: Bearer $TOKEN"; echo

echo "== 3. 链上写操作（应转发成功并返回链未启用业务错误，而非 401/403）=="
curl -s -X POST "localhost:$PORT/api/v1/chain/nft/totalSupply" -H "Authorization: Bearer $TOKEN"; echo

echo "== 4. 无 JWT 访问代理（应 401）=="
curl -s -o /dev/null -w "HTTP %{http_code}\n" localhost:$PORT/api/v1/chain/health

echo "== 5. 公网 Caddy -> linksprite health =="
curl -s --resolve nft.kokokongkong.com:443:127.0.0.1 https://nft.kokokongkong.com/api/health; echo
