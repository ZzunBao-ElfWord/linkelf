# 连接精灵后端一键部署：本地构建 -> scp 上传 -> 服务器 compose up
# 用法: powershell -File deploy\deploy.ps1 [-ForceCert]
#   -ForceCert: 强制上传本地 cert 目录覆盖服务器（默认跳过，防止覆盖新链证书）
param([switch]$ForceCert)
$ErrorActionPreference = 'Stop'

$deployDir = Split-Path -Parent $MyInvocation.MyCommand.Path   # linksprite-backend/deploy
$projDir   = Split-Path -Parent $deployDir                    # linksprite-backend
$mvn       = "C:\Users\k'k\tools\apache-maven-3.9.6\bin\mvn.cmd"
$remoteDir = '/opt/linksprite'
$jarName   = 'linksprite-backend-1.0.0.jar'

Write-Host '==> [1/4] Maven 构建'
Push-Location $projDir
& $mvn package -DskipTests -s settings.xml -q
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'Maven 构建失败' }
Pop-Location
Write-Host "    构建完成: target\$jarName"

Write-Host '==> [2/4] 准备服务器目录与 API-Key'
ssh stoat "mkdir -p $remoteDir"
$keyExists = ssh stoat "test -f $remoteDir/.env && echo yes || echo no"
if ($keyExists -ne 'yes') {
    # 首次部署：本地生成 32 位 hex API-Key 并上传
    $bytes = New-Object byte[] 16
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $key = ($bytes | ForEach-Object { $_.ToString('x2') }) -join ''
    $tmp = Join-Path $env:TEMP 'linksprite.env'
    Set-Content -Path $tmp -Value "LINKSPRITE_API_KEY=$key" -NoNewline -Encoding ascii
    scp $tmp "stoat:${remoteDir}/.env" | Out-Null
    Remove-Item $tmp
    Write-Host "    已生成 API-Key（请同步配置到 koko-admin 的 .env）: $key"
}
else {
    Write-Host '    .env 已存在，保留现有 API-Key'
}

Write-Host '==> [3/4] 上传构建产物'
scp "$projDir\target\$jarName" "$projDir\Dockerfile" "$projDir\docker-compose.yml" "stoat:${remoteDir}/" | Out-Null
$serverCertExists = ssh stoat "test -f ${remoteDir}/cert/sm_ca.crt && echo yes || echo no"
if ($serverCertExists -ne 'yes' -or $ForceCert) {
    scp -r "$projDir\cert" "stoat:${remoteDir}/" | Out-Null
    Write-Host '    cert 已上传'
} else {
    Write-Host '    cert 已存在，跳过上传（避免覆盖新链证书）'
}

Write-Host '==> [4/4] 服务器构建镜像并启动'
ssh stoat "cd $remoteDir && docker compose up -d --build"
if ($LASTEXITCODE -ne 0) { throw '服务器部署失败' }

Write-Host ''
Write-Host '部署完成，验证:'
Write-Host "  ssh stoat `"curl -s http://localhost:8080/api/health`"   # 容器健康"
Write-Host '  https://nft.kokokongkong.com/api/health                  # 公网入口(Caddy)'
