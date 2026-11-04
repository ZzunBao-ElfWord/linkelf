# ============================================================
# 连接精灵链 - Solidity 合约编译流水线 (Windows PowerShell 5+)
#
# 1. 下载 solc 0.8.20（与 pragma ^0.8.20 对齐）
# 2. 编译 linksprite-chain\contracts 下全部合约，输出 ABI + BIN
# 3. 拷贝产物到后端 linksprite-backend\src\main\resources\contracts
# 4. 运行 ABI 一致性校验（与 Java 内嵌 ABI 比对）
#
# 用法：powershell -ExecutionPolicy Bypass -File deploy\compile_contracts.ps1
# 网络受限时可手工下载 solc-windows.exe 放到 linksprite-chain\tools\
# ============================================================
$ErrorActionPreference = "Stop"

$SolcVersion = "0.8.20"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ChainDir = Resolve-Path (Join-Path $ScriptDir "..")
$ContractDir = Join-Path $ChainDir "contracts"
$ToolsDir = Join-Path $ChainDir "tools"
$OutDir = Join-Path $ChainDir "build\contracts"
$BackendDir = Resolve-Path (Join-Path $ChainDir "..\linksprite-backend")
$ResourceContractsDir = Join-Path $BackendDir "src\main\resources\contracts"

# AIAccount 由 AIAccountFactory 内嵌部署，一并编译
$TargetContracts = @(
    "KYCRegistry", "TransferPolicy", "AuthorizationRegistry", "LinkSpriteNFT",
    "SpriteComputeToken", "AIAccountFactory", "Paymaster", "AIAccount"
)

New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
New-Item -ItemType Directory -Force -Path $ResourceContractsDir | Out-Null

# 1. 获取 solc
$SolcBin = Join-Path $ToolsDir "solc-windows-$SolcVersion.exe"
if (-not (Test-Path $SolcBin)) {
    $SolcUrl = "https://github.com/ethereum/solidity/releases/download/v$SolcVersion/solc-windows.exe"
    Write-Host "[1/4] 下载 solc $SolcVersion ..."
    Write-Host "      $SolcUrl"
    try {
        Invoke-WebRequest -Uri $SolcUrl -OutFile $SolcBin -UseBasicParsing
    } catch {
        Write-Host "[错误] solc 下载失败（网络受限）。请手工下载 solc-windows.exe 放到："
        Write-Host "       $SolcBin"
        Write-Host "       下载地址: $SolcUrl"
        exit 1
    }
} else {
    Write-Host "[1/4] 使用已存在的 solc: $SolcBin"
}
& $SolcBin --version | Select-Object -First 2

# 2. 编译全部合约
Write-Host "[2/4] 编译合约..."
$SolSources = Get-ChildItem -Path $ContractDir -Filter *.sol | ForEach-Object { $_.FullName }
& $SolcBin @(
    "--base-path", $ContractDir,
    "--allow-paths", $ContractDir,
    "--optimize", "--optimize-runs", "200",
    "--abi", "--bin",
    "-o", $OutDir,
    "--overwrite"
) $SolSources
if ($LASTEXITCODE -ne 0) {
    Write-Host "[错误] solc 编译失败"
    exit 1
}

# 3. 拷贝目标产物到后端资源目录
Write-Host "[3/4] 拷贝 ABI/BIN 到后端资源目录: $ResourceContractsDir"
$missing = $false
foreach ($name in $TargetContracts) {
    $bin = Join-Path $OutDir "$name.bin"
    $abi = Join-Path $OutDir "$name.abi"
    if (-not (Test-Path $bin) -or -not (Test-Path $abi)) {
        Write-Host "  [缺失] $name.abi/.bin"
        $missing = $true
        continue
    }
    Copy-Item $bin (Join-Path $ResourceContractsDir "$name.bin") -Force
    Copy-Item $abi (Join-Path $ResourceContractsDir "$name.abi") -Force
    Write-Host "  [就绪] $name"
}
if ($missing) {
    Write-Host "[错误] 部分合约产物缺失，请检查编译输出"
    exit 1
}

# 4. ABI 一致性校验
Write-Host "[4/4] ABI 一致性校验..."
$mvnPath = $null
$mvnCmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($mvnCmd) {
    $mvnPath = $mvnCmd.Source
} else {
    $portableMvn = Join-Path $env:USERPROFILE "tools\apache-maven-3.9.6\bin\mvn.cmd"
    if (Test-Path $portableMvn) {
        $mvnPath = $portableMvn
    } else {
        Write-Host "[错误] 未找到 mvn.cmd，请安装 Maven 或放入 PATH"
        exit 1
    }
}

$mvnArgs = @()
if (Test-Path (Join-Path $BackendDir "settings.xml")) {
    $mvnArgs += @("-s", (Join-Path $BackendDir "settings.xml"))
}
$mvnArgs += @("-q", "compile", "exec:java",
    "-Dexec.mainClass=com.linksprite.tools.AbiConsistencyChecker",
    "-Dexec.classpathScope=compile")

Push-Location $BackendDir
try {
    & $mvnPath @mvnArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[错误] ABI 一致性校验失败，请按差异修正 Java 内嵌 ABI"
        exit 1
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "完成。产物目录: $ResourceContractsDir"
Write-Host "部署合约：启动后端（fisco.enabled=true）后 POST /api/deploy/contracts"
