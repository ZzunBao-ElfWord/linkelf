#!/usr/bin/env bash
M=$(docker ps --format '{{.Names}}' | grep -iE 'mongo|database' | head -1)
echo "CONTAINER=$M"
WUID=01M4DWCZGNVSDAJF76JPEQ8ZDH
docker exec "$M" mongosh --quiet revolt --eval "const w=db.chain_wallets.findOne({_id:'$WUID'},{privateKey:0}); printjson(w); print('hasPrivateKey='+(!!db.chain_wallets.findOne({_id:'$WUID'}).privateKey))"
TOKEN=$(curl -sk --resolve openelfai.cn:443:127.0.0.1 -X POST https://openelfai.cn/koko/auth/phone/login -H 'Content-Type: application/json' -d '{"phone":"19900000000","code":"888888"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -sk -o /dev/null -w 'no-auth=%{http_code}\n' --resolve openelfai.cn:443:127.0.0.1 "https://openelfai.cn/koko/wallet/$WUID"
rm -f /tmp/w.sh
