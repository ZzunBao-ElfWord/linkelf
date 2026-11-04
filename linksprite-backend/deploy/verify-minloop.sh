#!/usr/bin/env bash
KEY=$(grep '^LINKSPRITE_API_KEY=' /opt/linksprite/.env | cut -d= -f2)
echo "== 1. 新端点 /auth/list（dev 模式应返回业务错误而非 404）=="
curl -sk --resolve nft.kokokongkong.com:443:127.0.0.1 https://nft.kokokongkong.com/api/auth/list -H "X-API-Key: $KEY"; echo
echo "== 2. 钱包端点回归（应正常生成）=="
curl -sk --resolve nft.kokokongkong.com:443:127.0.0.1 -X POST https://nft.kokokongkong.com/api/wallet/create -H "X-API-Key: $KEY" | head -c 120; echo
echo "== 3. 管理后台页面 =="
curl -sk -o /dev/null -w "HTTP %{http_code}\n" --resolve admin.kokokongkong.com:443:127.0.0.1 https://admin.kokokongkong.com/
echo "== 4. BFF 代理新端点（无 JWT 应 401，证明路由透传）=="
curl -sk -o /dev/null -w "HTTP %{http_code}\n" --resolve admin.kokokongkong.com:443:127.0.0.1 https://admin.kokokongkong.com/api/v1/chain/auth/list
