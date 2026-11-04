#!/bin/sh
# 作品收单金丝雀：配置 mysql profile -> 重建并启动 -> 等就绪 -> 提交一条样本 -> 核对落库
# 用法（服务器上）: sh /tmp/canary.sh
set -e
cd /opt/linksprite

ENV_FILE=/opt/linksprite/.env
touch "$ENV_FILE"
grep -q '^SPRING_PROFILES_ACTIVE=' "$ENV_FILE" || printf '\nSPRING_PROFILES_ACTIVE=mysql\n' >> "$ENV_FILE"
echo "--- .env ---"
cat "$ENV_FILE"
echo

echo "--- compose up (含 MySQL) ---"
docker compose up -d --build

echo "--- 等待 linksprite-mysql healthy ---"
i=0
while true; do
  st=$(docker inspect -f '{{.State.Health.Status}}' linksprite-mysql 2>/dev/null || true)
  if [ "$st" = "healthy" ]; then break; fi
  i=$((i+1))
  if [ "$i" -gt 60 ]; then echo "MySQL 未就绪"; docker logs --tail 40 linksprite-mysql; exit 1; fi
  sleep 3
done
echo "mysql healthy"

echo "--- 等待后端就绪（走 Caddy 回环） ---"
i=0
while true; do
  if curl -sk --resolve nft.kokokongkong.com:443:127.0.0.1 -fsS https://nft.kokokongkong.com/api/health >/dev/null 2>&1; then break; fi
  i=$((i+1))
  if [ "$i" -gt 60 ]; then echo "后端未就绪"; docker logs --tail 40 linksprite-backend; exit 1; fi
  sleep 3
done
echo "backend healthy"

KEY=$(grep '^LINKSPRITE_API_KEY=' "$ENV_FILE" | cut -d= -f2)

cat > /tmp/sample.json <<'JSON'
{
  "seriesId": "SERIES-CANARY-20261015",
  "generatorJobId": "SERIES-CANARY-20261015",
  "totalVersions": 100,
  "format": "png",
  "coverUrl": "https://cdn.example.com/koko/0001.png",
  "versions": [
    {"index": 1, "filename": "0001.png", "rarityScore": 42, "rarityTier": "rare", "traits": {"背景": "星空"}},
    {"index": 2, "filename": "0002.png", "rarityScore": 17, "rarityTier": "common", "traits": {"背景": "城市"}}
  ],
  "creatorName": "KOKO 空空",
  "creatorAvatarUrl": "https://cdn.example.com/koko/avatar.png",
  "creatorIntro": "空空的创作者",
  "issueType": "独立发行",
  "productName": "空空星河系列",
  "blindBoxCount": 1,
  "priceTier": "59元",
  "presaleMonth": "2026-11",
  "presaleThresholdQty": "100",
  "presaleRuleHtml": "<p>预售说明</p>",
  "works": [
    {"workName": "星河 0001", "workCategory": "数字艺术", "workSummary": "简介",
     "detailImages": ["https://cdn.example.com/koko/0001.png"], "onHomepage": true,
     "jingtanPhone": "19900000000", "winningProbability": 100}
  ],
  "settlementTiers": [{"feeRange": "0-100", "qtyRange": "1-10", "shareRatio": "0.5"}],
  "commission": {"android": {"payMethod": "alipay", "alipayAccount": "demo@example.com"}}
}
JSON

echo "--- POST /api/work-submissions ---"
curl -sk --resolve nft.kokokongkong.com:443:127.0.0.1 -X POST \
  https://nft.kokokongkong.com/api/work-submissions \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  --data-binary @/tmp/sample.json
echo

echo "--- 落库核对 ---"
docker exec linksprite-mysql mysql -uroot -plinksprite linksprite \
  -e 'SELECT id, series_id, creator_name, product_name, total_versions, format, status, created_at FROM work_submission \G' 2>/dev/null

echo "--- 幂等复测（同 seriesId 再提交一次，应返回同 id） ---"
curl -sk --resolve nft.kokokongkong.com:443:127.0.0.1 -X POST \
  https://nft.kokokongkong.com/api/work-submissions \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  --data-binary @/tmp/sample.json
echo
docker exec linksprite-mysql mysql -uroot -plinksprite linksprite \
  -e 'SELECT COUNT(*) AS rows_total FROM work_submission' 2>/dev/null
