<#
.SYNOPSIS
  V-VOICE のビルドに必要な取得物を揃える。冪等。

.DESCRIPTION
  リポジトリに含めていない以下を公式配布元から取得して配置する。

    localrepo/                      VOICEVOX CORE の Android AAR (ローカル Maven リポジトリ)
    app/src/main/jniLibs/*/         VOICEVOX ONNX Runtime
    app/src/main/assets/voicevox/   Open JTalk 辞書 + 音声モデル (2.vvm)

  合計約 190MB。初回のみダウンロードが走る。

.PARAMETER Arm64Only
  エミュレータ用の x86_64 ネイティブライブラリを取得しない。実機だけで使うなら
  これを付けると 21MB 減らせる。既定では両方取得する。

.PARAMETER Force
  取得済みでも再取得する。

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/fetch_assets.ps1

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/fetch_assets.ps1 -Arm64Only
#>
[CmdletBinding()]
param(
    # bool ではなく switch にしてある。powershell -File は引数を文字列として
    # 渡すため、-IncludeX86:$false のような書き方が通らない。
    [switch] $Arm64Only,
    [switch] $Force
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$CoreVersion = '0.16.4'
$OrtVersion = '1.17.3'
# 同梱する音声モデル。2.vvm = 九州そら (ノーマル / あまあま / セクシー / ツンツン)。
$ModelFile = '2.vvm'

$Root = Split-Path -Parent $PSScriptRoot
$Work = Join-Path $Root '.fetch-cache'
$Assets = Join-Path $Root 'app/src/main/assets/voicevox'
$JniLibs = Join-Path $Root 'app/src/main/jniLibs'
$LocalRepo = Join-Path $Root 'localrepo'

New-Item -ItemType Directory -Force $Work | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Step($msg) { Write-Host "[fetch] $msg" }

function Get-File($url, $dest) {
    if ((Test-Path $dest) -and -not $Force) {
        Step "skip (cached): $(Split-Path -Leaf $dest)"
        return
    }
    Step "downloading $(Split-Path -Leaf $dest)"
    Invoke-WebRequest -Uri $url -OutFile $dest -TimeoutSec 600 -UseBasicParsing
}

# ---------------------------------------------------------------- 1. CORE AAR
# java_packages.zip を展開すると Maven リポジトリのディレクトリ構造がそのまま出てくる。
# settings.gradle.kts がこれを maven { url = ... } で参照する。
$javaPkg = Join-Path $Work 'java_packages.zip'
Get-File "https://github.com/VOICEVOX/voicevox_core/releases/download/$CoreVersion/java_packages.zip" $javaPkg

if ($Force -and (Test-Path $LocalRepo)) { Remove-Item $LocalRepo -Recurse -Force }
if (-not (Test-Path (Join-Path $LocalRepo "jp/hiroshiba/voicevoxcore/voicevoxcore-android/$CoreVersion"))) {
    Step 'extracting VOICEVOX CORE AAR -> localrepo/'
    $tmp = Join-Path $Work 'maven'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    [System.IO.Compression.ZipFile]::ExtractToDirectory($javaPkg, $tmp)
    New-Item -ItemType Directory -Force $LocalRepo | Out-Null
    Copy-Item (Join-Path $tmp 'jp') $LocalRepo -Recurse -Force
    # ソースと javadoc はビルドに不要。12MB ほど減る。
    Get-ChildItem $LocalRepo -Recurse -Include '*-sources.jar', '*-javadoc.jar' | Remove-Item -Force
}

# -------------------------------------------------------- 2. ONNX Runtime (.so)
$abis = @{ 'arm64-v8a' = 'android-arm64' }
if (-not $Arm64Only) { $abis['x86_64'] = 'android-x64' }

foreach ($abi in $abis.Keys) {
    $target = Join-Path $JniLibs "$abi/libvoicevox_onnxruntime.so"
    if ((Test-Path $target) -and -not $Force) {
        Step "skip (cached): $abi/libvoicevox_onnxruntime.so"
        continue
    }
    $slug = $abis[$abi]
    $tgz = Join-Path $Work "ort-$slug.tgz"
    Get-File "https://github.com/VOICEVOX/onnxruntime-builder/releases/download/voicevox_onnxruntime-$OrtVersion/voicevox_onnxruntime-$slug-$OrtVersion.tgz" $tgz

    $ext = Join-Path $Work "ort-$slug"
    if (Test-Path $ext) { Remove-Item $ext -Recurse -Force }
    New-Item -ItemType Directory -Force $ext | Out-Null
    tar -xzf $tgz -C $ext
    if ($LASTEXITCODE -ne 0) { throw "tar failed for $tgz" }

    $so = Get-ChildItem $ext -Recurse -Filter 'libvoicevox_onnxruntime.so' | Select-Object -First 1
    if (-not $so) { throw "libvoicevox_onnxruntime.so not found in $tgz" }
    New-Item -ItemType Directory -Force (Split-Path -Parent $target) | Out-Null
    Copy-Item $so.FullName $target -Force
    Step "placed $abi/libvoicevox_onnxruntime.so"
}

# ---------------------------------------------------------- 3. Open JTalk 辞書
# 公式ダウンローダに任せる。辞書のバージョンを CORE 側に合わせてくれる。
$dictDest = Join-Path $Assets 'dict'
if ((Test-Path (Join-Path $dictDest 'sys.dic')) -and -not $Force) {
    Step 'skip (cached): Open JTalk dictionary'
} else {
    $dl = Join-Path $Work 'download.exe'
    Get-File "https://github.com/VOICEVOX/voicevox_core/releases/download/$CoreVersion/download-windows-x64.exe" $dl

    $dlOut = Join-Path $Work 'vvdict'
    if (Test-Path $dlOut) { Remove-Item $dlOut -Recurse -Force }
    Step 'downloading Open JTalk dictionary (about 100MB)'
    & $dl --only dict -o $dlOut
    if ($LASTEXITCODE -ne 0) { throw 'dictionary download failed' }

    $src = Get-ChildItem $dlOut -Recurse -Directory -Filter 'open_jtalk_dic_utf_8-*' | Select-Object -First 1
    if (-not $src) { throw 'open_jtalk_dic_utf_8-* not found' }
    if (Test-Path $dictDest) { Remove-Item $dictDest -Recurse -Force }
    New-Item -ItemType Directory -Force (Split-Path -Parent $dictDest) | Out-Null
    Copy-Item $src.FullName $dictDest -Recurse -Force
    Step 'placed assets/voicevox/dict/'
}

# ------------------------------------------------------------- 4. 音声モデル
# モデルは利用規約への同意が要るため、ダウンローダが対話で確認してくる。
$modelDest = Join-Path $Assets "model/$ModelFile"
if ((Test-Path $modelDest) -and -not $Force) {
    Step "skip (cached): $ModelFile"
} else {
    Write-Host ''
    Write-Host '  音声モデルのダウンロードには利用規約への同意が必要です。' -ForegroundColor Yellow
    Write-Host '  この後、規約が表示され y/n の入力を求められます。' -ForegroundColor Yellow
    Write-Host '  対話可能なターミナルで実行してください（CI やパイプ経由では止まります）。' -ForegroundColor Yellow
    Write-Host '  https://voicevox.hiroshiba.jp/term/' -ForegroundColor Yellow
    Write-Host ''
    $dl = Join-Path $Work 'download.exe'
    Get-File "https://github.com/VOICEVOX/voicevox_core/releases/download/$CoreVersion/download-windows-x64.exe" $dl

    $dlOut = Join-Path $Work 'vvmodel'
    if (Test-Path $dlOut) { Remove-Item $dlOut -Recurse -Force }
    & $dl --only models --models-pattern $ModelFile -o $dlOut
    if ($LASTEXITCODE -ne 0) {
        throw 'model download failed. 規約に同意しなかった場合と、対話できない環境で実行した場合にここで止まります。'
    }

    $vvm = Get-ChildItem $dlOut -Recurse -Filter $ModelFile | Select-Object -First 1
    if (-not $vvm) { throw "$ModelFile not found" }
    New-Item -ItemType Directory -Force (Split-Path -Parent $modelDest) | Out-Null
    Copy-Item $vvm.FullName $modelDest -Force
    Step "placed assets/voicevox/model/$ModelFile"
}

# -------------------------------------------------------------------- summary
Write-Host ''
Step 'done. placed:'
foreach ($p in @($LocalRepo, $JniLibs, $Assets)) {
    if (Test-Path $p) {
        $mb = (Get-ChildItem $p -Recurse -File | Measure-Object Length -Sum).Sum / 1MB
        '  {0,-40} {1,8:N1} MB' -f $p.Replace("$Root\", ''), $mb
    }
}
Write-Host ''
Write-Host '  次: .\gradlew.bat :app:assembleDebug'
