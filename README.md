# V-VOICE

架空の「特殊通信端末」風の音声合成アプリ。入力した文章を [VOICEVOX](https://voicevox.hiroshiba.jp/) で読み上げます。

Android 版と、ブラウザだけで動く単一 HTML 版があります。

---

## これは何か

ドラマの小道具のような業務端末に見えることを狙った、個人的な習作です。黒い画面、細い罫線、ステータス表示、通信ログ、グレーアウトされた機能で「実在しそうな業務システム」に寄せています。

**翻訳機能は実装していません。** `TRANSLATION [ LOCKED ]` の表示は意図的な演出で、操作できません。`STABILITY` / `ENCRYPTION` などの一部パラメータも同様の飾りです。実際に効くのは声・速度・ピッチ・抑揚・区切りの間だけです。

---

## 2 つの版

| | Android 版 | HTML 版 |
|---|---|---|
| 音声合成 | **端末内の VOICEVOX CORE** | VOICEVOX ENGINE (HTTP API) |
| 通信 | **不要** | localhost へのアクセスが必要 |
| 声 | 九州そら（4 スタイル） | ENGINE にある全キャラクター |
| 用途 | 実際に持ち歩いて使う | 声を選ぶ・試す |

HTML 版は ENGINE に繋がらない場合、ブラウザ標準の読み上げ (Web Speech API) に自動で切り替わります。Android 版も CORE の初期化に失敗した場合は端末の TTS にフォールバックします。

---

## 機能

- **MAIN** — 文章入力、SPEAK、クイックコマンド
- **LOG** — 発話履歴（時刻・本文・使用した声）。メモリ上のみで、終了すると消えます
- **SYSTEM** — 呼び名、クイックコマンドの編集、声、速度、ピッチ、抑揚、区切りの間、クレジット

**クイックコマンドは押した瞬間に喋ります。** 確認の一手間は挟みません。合成結果はキャッシュするので、同じ文言の 2 回目以降は待ち時間ゼロです。

**呼び名は SYSTEM > IDENTITY で変更できます。** クイックコマンドの `私は{self}です` や `{other}さん` に差し込まれ、入力した瞬間にボタンの表示が変わります。既定値は中立な名前なので、好きな名前に置き換えてください。

**クイックコマンドの中身は SYSTEM > QUICK COMMAND で編集できます。** 文言の書き換え、行の追加・削除（最大 12 件）、既定値へのリセットができます。`{self}` `{other}` `{other2}` と書いた箇所に IDENTITY の呼び名が差し込まれます。RESET は誤操作を防ぐため 2 度押しです。

**設定は保存されます。** 呼び名・クイックコマンド・声・速度・ピッチ・抑揚・区切りの間は、次に開いたときに元どおりになります（Android 版は端末内、HTML 版はブラウザの localStorage）。LOG だけは保存しません。

**抑揚 (INTONATION)** は 3 段階です。`NATURAL` / `FLAT` / `MONOTONE` の順に平坦になり、合成音声らしい淡々とした話し方になります。VOICEVOX では `intonationScale` を直接操作し、標準 TTS へフォールバックしたときは句読点で文を分割して抑揚を崩します。

再生中の波形は、VOICEVOX 経路では**実際の PCM から描いています**。標準 TTS のときは出力音声にアクセスできないため、発話に同期した擬似波形です。

---

## HTML 版の使い方

[VOICEVOX ENGINE](https://github.com/VOICEVOX/voicevox_engine) を起動したうえで、**ローカル HTTP サーバー経由で開いてください。**

```powershell
python -m http.server 8001
# ブラウザで http://localhost:8001/voice-tester.html
```

`file://` で直接開くと Origin が `null` になり、ENGINE の CORS 設定に弾かれます。

SYSTEM タブの `SYNTHESIS` 行をタップすると VOICEVOX と標準 TTS を切り替えられます。`ENDPOINT` は既定で `http://localhost:50021` です。

---

## Android 版のビルド

### 必要なもの

- Android Studio（JDK 同梱のもの）
- Android SDK Platform 34

**NDK と CMake は不要です。** VOICEVOX CORE は公式の Android AAR にビルド済みの JNI ライブラリが入っているため、ネイティブビルドは発生しません。

### 手順

```powershell
# 1. 取得物を揃える（初回のみ。約 190MB のダウンロード）
powershell -ExecutionPolicy Bypass -File scripts/fetch_assets.ps1

# 2. ビルド
.\gradlew.bat :app:assembleDebug
```

`fetch_assets.ps1` は冪等です。取得済みならスキップします。音声モデルのダウンロード時に**利用規約への同意を対話で求められます**。

実機のみで使う場合は、エミュレータ用の x86_64 ライブラリを省いて 21MB 減らせます。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/fetch_assets.ps1 -IncludeX86:$false
```

### 初回起動について

VOICEVOX CORE は辞書もモデルも実ファイルのパスでしか受け取れないため、**初回起動時に約 158MB を内部ストレージへ展開します。** 起動画面に `EXTRACTING` と進捗が出ます。2 回目以降はスキップされます。

その分、端末の占有量は APK と展開分の合計になります。

---

## リポジトリに入っていないもの

辞書・音声モデル・ネイティブライブラリ・AAR は容量が大きいためコミットしていません。すべて `scripts/fetch_assets.ps1` が公式配布元から取得します。

```
app/src/main/assets/voicevox/   辞書 102MB + 音声モデル 55MB
app/src/main/jniLibs/           ONNX Runtime
localrepo/                      VOICEVOX CORE の AAR
```

---

## クレジット

**VOICEVOX:九州そら**

音声合成に [VOICEVOX](https://voicevox.hiroshiba.jp/) を使用しています。アプリ内では SYSTEM タブの CREDIT セクションに表示しています。

### ライセンスと規約

| 対象 | ライセンス / 規約 |
|---|---|
| VOICEVOX CORE | [MIT License](https://github.com/VOICEVOX/voicevox_core/blob/main/LICENSE) |
| VOICEVOX ONNX Runtime | [onnxruntime-builder](https://github.com/VOICEVOX/onnxruntime-builder) |
| 音声モデル (VVM) | [VOICEVOX 音声モデル 利用規約](https://github.com/VOICEVOX/voicevox_vvm) |
| 九州そら | [東北ずん子プロジェクト 音源利用規約](https://zunko.jp/con_ongen_kiyaku.html) |
| Open JTalk 辞書 | Modified BSD（`assets/voicevox/dict/COPYING`） |

VOICEVOX 音声モデルの利用規約は、商用・非商用を問わない利用と、**アプリケーションへの組み込み再配布を許諾しています。** 九州そらの音源規約もクレジット表記を条件に商用・非商用の利用を認めています。

このアプリを改変・再配布する場合は、上記の規約を確認し、クレジット表記を維持してください。

---

## 免責

個人的な習作です。特定の団体・製品・実在の人物とは関係ありません。アプリ内に表示される通信状態やセキュリティ関連の表示は、すべて演出上の架空のものです。
