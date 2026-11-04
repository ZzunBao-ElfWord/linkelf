#!/bin/bash
# 服务器侧合约部署脚本：读取 API-Key，调用部署接口，写回合约地址到 .env
set -e
ENV_FILE=/opt/linksprite/.env
KEY=$(grep LINKSPRITE_API_KEY "$ENV_FILE" | cut -d= -f2)

echo "==> 调用 /api/deploy/contracts"
curl -s -X POST \
  --resolve nft.kokokongkong.com:443:127.0.0.1 \
  -H "X-API-Key: $KEY" \
  -H "Content-Type: application/json" \
  https://nft.kokokongkong.com/api/deploy/contracts > /tmp/deploy_result.json

cat /tmp/deploy_result.json | head -c 3000
echo ""

# 解析合约地址并写回 .env（幂等：先删旧行再追加）
python3 - <<'EOF'
import json, re

with open('/tmp/deploy_result.json') as f:
    data = json.load(f)

contracts = data.get('data', {}).get('contracts', [])
if not contracts:
    print('[错误] 部署结果为空，未写回地址')
    raise SystemExit(1)

mapping = {
    'KYCRegistry': 'CONTRACT_KYC_REGISTRY',
    'TransferPolicy': 'CONTRACT_TRANSFER_POLICY',
    'AuthorizationRegistry': 'CONTRACT_AUTHORIZATION_REGISTRY',
    'LinkSpriteNFT': 'CONTRACT_LINKSPRITE_NFT',
    'SpriteComputeToken': 'CONTRACT_SPRITE_COMPUTE_TOKEN',
    'AIAccountFactory': 'CONTRACT_AI_ACCOUNT_FACTORY',
    'Paymaster': 'CONTRACT_PAYMASTER',
}

env_path = '/opt/linksprite/.env'
with open(env_path) as f:
    lines = f.readlines()

# 删除旧的 CONTRACT_* 行
keys = set(mapping.values())
lines = [l for l in lines if not any(l.startswith(k + '=') for k in keys)]

for c in contracts:
    name, addr = c['name'], c['address']
    if name in mapping:
        lines.append(f"{mapping[name]}={addr}\n")
        print(f"  {mapping[name]}={addr}")

with open(env_path, 'w') as f:
    f.writelines(lines)

wiring = data.get('data', {}).get('wiring', [])
print('==> wiring:')
for w in wiring:
    print(' ', w)
EOF
