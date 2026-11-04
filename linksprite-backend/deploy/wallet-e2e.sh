#!/usr/bin/env bash
# 链上钱包端到端验证：生成→托管→查询→幂等
set -e
KEY=$(grep '^LINKSPRITE_API_KEY=' /opt/linksprite/.env | cut -d= -f2)
NFT="curl -sk --resolve nft.kokokongkong.com:443:127.0.0.1"
KOKO="curl -sk --resolve openelfai.cn:443:127.0.0.1"

echo "== 1. linksprite 健康检查（经 Caddy）=="
$NFT https://nft.kokokongkong.com/api/health; echo

echo "== 2. 直接调钱包生成（带 API-Key）=="
$NFT -X POST https://nft.kokokongkong.com/api/wallet/create -H "X-API-Key: $KEY"; echo

echo "== 3. 无 API-Key 应 401 =="
$NFT -o /dev/null -w "HTTP %{http_code}\n" -X POST https://nft.kokokongkong.com/api/wallet/create

echo "== 4. 测试号登录拿会话 =="
LOGIN=$($KOKO -X POST https://openelfai.cn/koko/auth/phone/login \
  -H 'Content-Type: application/json' -d '{"phone":"19900000000","code":"888888"}')
WUID=$(echo "$LOGIN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["user_id"])')
TOKEN=$(echo "$LOGIN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
echo "user_id=$WUID token=${#TOKEN}chars"

echo "== 5. 查询钱包（自动生成）=="
W1=$($KOKO "https://openelfai.cn/koko/wallet/$WUID" -H "X-Session-Token: $TOKEN")
echo "$W1"
ADDR1=$(echo "$W1" | python3 -c 'import sys,json;print(json.load(sys.stdin)["address"])')

echo "== 6. 再次查询（应幂等，同地址）=="
ADDR2=$($KOKO "https://openelfai.cn/koko/wallet/$WUID" -H "X-Session-Token: $TOKEN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["address"])')
[ "$ADDR1" = "$ADDR2" ] && echo "IDEMPOTENT_OK $ADDR1" || echo "MISMATCH: $ADDR1 vs $ADDR2"

echo "== 7. Mongo 托管记录 =="
docker exec database mongosh --quiet revolt --eval "const w=db.chain_wallets.findOne({_id:'$WUID'},{privateKey:0}); printjson(w); print('hasPrivateKey='+(!!db.chain_wallets.findOne({_id:'$WUID'}).privateKey))"

echo "== 8. 无会话凭证应 401 =="
$KOKO -o /dev/null -w "HTTP %{http_code}\n" "https://openelfai.cn/koko/wallet/$WUID"
