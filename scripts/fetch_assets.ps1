<#
.SYNOPSIS
  V-VOICE のビルドに必要な取得物を揃える。冪等。

.DESCRIPTION
  リポジトリに含めていない以下を公式配布元から取得して配置する。

    localrepo/                      VOICEVOX CORE の Android AAR (ローカル Maven リポジトリ)
    app/src/main/jniLibs/*/         VOICEVOX ONNX Runtime
    app/src/main/assets/voicevox/   Open JTalk 辞書 + 音声モデル (2.vvm)

  合計約 190MB。初回のみダウンロードが走る。

  音声モデル (VVM) の取得には利用規約への同意が必要で、既定では公式ダウンローダの
  対話プロンプトで確認する。-AcceptTerms を指定すると、実行者が規約に事前同意して
  いる前提で、モデル実体 (VOICEVOX/voicevox_vvm の GitHub Releases アセット) を
  直接取得し対話をスキップする。同意そのものを省略する機能ではない。
  CI で使う場合は、ワークフロー側で規約確認済みであることを踏まえて指定すること。

  (公式ダウンローダの対話プロンプトを標準入力経由で自動応答する方式も試したが、
  規約テキスト表示に使われる `minus` ページャーが日本語のマルチバイト文字境界で
  パニックし、以後の入力を毎回不正な値として拒否するようになる既知の問題がある
  ため、モデル実体の直接取得に切り替えている。)

.PARAMETER Arm64Only
  エミュレータ用の x86_64 ネイティブライブラリを取得しない。実機だけで使うなら
  これを付けると 21MB 減らせる。既定では両方取得する。

.PARAMETER Force
  取得済みでも再取得する。

.PARAMETER AcceptTerms
  音声モデル (VVM) ダウンロード時の利用規約同意プロンプトを自動で承諾し、
  対話なしで続行する。既定は $false（従来どおり対話で同意を求める）。

  これを指定してよいのは、実行者（CI 実行者を含む）が
  https://voicevox.hiroshiba.jp/term/ の利用規約を事前に確認・同意済みの
  場合に限る。同意そのものを省略する機能ではない。

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/fetch_assets.ps1

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/fetch_assets.ps1 -Arm64Only

.EXAMPLE
  # CI など、規約確認済みの実行者が非対話で流す場合のみ
  powershell -ExecutionPolicy Bypass -File scripts/fetch_assets.ps1 -AcceptTerms
#>
[CmdletBinding()]
param(
    # bool ではなく switch にしてある。powershell -File は引数を文字列として
    # 渡すため、-IncludeX86:$false のような書き方が通らない。
    [switch] $Arm64Only,
    [switch] $Force,
    # 既定値は必ず $false のままにすること。人間が手で叩いたときは
    # 従来どおり対話で同意を求める挙動を維持する。
    [switch] $AcceptTerms
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
    $dlOut = Join-Path $Work 'vvmodel'
    if (Test-Path $dlOut) { Remove-Item $dlOut -Recurse -Force }

    if ($AcceptTerms) {
        # ダウンローダ (Rust 製 CLI) に -y 等の非対話フラグは存在しない。標準入力に
        # "y" を渡す方式も試したが、規約テキスト表示に使われる `minus` ページャーが
        # 日本語のマルチバイト文字境界でパニックし、その後の標準入力の扱いが壊れて
        # 常に不正な入力として弾かれる (GitHub Actions の pwsh、Windows PowerShell
        # 5.1 の両方で再現。BOM 除去やエンコーディング指定でも解消しなかった)。
        #
        # モデル実体は VOICEVOX/voicevox_vvm の GitHub Releases に通常の公開アセット
        # として置かれており、ダウンローダは規約表示の窓口に過ぎない
        # (crates/downloader は octocrab 経由でこのリポジトリの Release Asset を
        # 取得しているだけで、GitHub 側のアセット配信自体に同意チェックは無い)。
        # -AcceptTerms は「実行者が規約に事前同意している」前提の機能であり、
        # ダウンローダの対話をスキップしても規約遵守義務がなくなるわけではない。
        Step "AcceptTerms: 利用規約に事前同意済みとして、モデルを直接取得します (https://voicevox.hiroshiba.jp/term/)"
        $modelUrl = "https://github.com/VOICEVOX/voicevox_vvm/releases/download/$CoreVersion/$ModelFile"
        New-Item -ItemType Directory -Force $dlOut | Out-Null
        # Invoke-WebRequest は失敗時に例外を投げる ($ErrorActionPreference = 'Stop' 済み)
        # ため、ここでは $LASTEXITCODE によるチェックを行わない。
        Invoke-WebRequest -Uri $modelUrl -OutFile (Join-Path $dlOut $ModelFile) -TimeoutSec 600 -UseBasicParsing
    } else {
        Write-Host ''
        Write-Host '  音声モデルのダウンロードには利用規約への同意が必要です。' -ForegroundColor Yellow
        Write-Host '  この後、規約が表示され y/n の入力を求められます。' -ForegroundColor Yellow
        Write-Host '  対話可能なターミナルで実行してください（CI やパイプ経由では止まります）。' -ForegroundColor Yellow
        Write-Host '  https://voicevox.hiroshiba.jp/term/' -ForegroundColor Yellow
        Write-Host ''
        $dl = Join-Path $Work 'download.exe'
        Get-File "https://github.com/VOICEVOX/voicevox_core/releases/download/$CoreVersion/download-windows-x64.exe" $dl
        & $dl --only models --models-pattern $ModelFile -o $dlOut
        if ($LASTEXITCODE -ne 0) {
            throw 'model download failed. 規約に同意しなかった場合と、対話できない環境で -AcceptTerms なしに実行した場合にここで止まります。'
        }
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
