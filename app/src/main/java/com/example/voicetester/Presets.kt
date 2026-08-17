package com.example.voicetester

/**
 * 抑揚の指定。
 *
 * VOICEVOX では [intonationScale] を直接いじれるので文を分割する必要はない。
 * OS TTS へフォールバックしたときだけ [split] / [gapMs] を使って抑揚を潰す。
 */
data class Intonation(
    val label: String,
    val split: Boolean,
    val gapMs: Int,
    val intonationScale: Double,
)

val INTONATIONS = listOf(
    Intonation("NATURAL", split = false, gapMs = 0, intonationScale = 1.0),
    Intonation("FLAT", split = true, gapMs = 180, intonationScale = 0.5),
    Intonation("MONOTONE", split = true, gapMs = 340, intonationScale = 0.0),
)

/**
 * クイックコマンドの既定値。固有名詞は SYSTEM > IDENTITY で差し替えられるようテンプレートにしてある。
 * `{self}` = UNIT NAME、`{other}` = CONTACT 01、`{other2}` = CONTACT 02。
 *
 * 中身は SYSTEM > QUICK COMMAND から編集でき、編集後は端末に保存される。
 * ここはあくまで初期値と RESET の戻り先。
 */
val QUICK_COMMANDS = listOf(
    "私は{self}です",
    "よろしくね",
    "超キケン！ 超キケン！",
    "超びっくり！ 超びっくり！",
    "{other2}さん",
    "どうかしました？",
    "{other}さん",
    "了解したよ",
)

/** クイックコマンドの上限。MAIN の 2 列グリッドが縦に伸びすぎない範囲に留める。 */
const val QUICK_MAX = 12

/**
 * 呼び名。SYSTEM > IDENTITY でいつでも変更できる。
 *
 * 既定値は特定の作品や実在の人物を指さない中立なものにしてある。
 * 好みの名前に置き換えて使う想定。
 */
data class Identity(
    val self: String = DEFAULT_SELF,
    val other: String = DEFAULT_OTHER,
    val other2: String = DEFAULT_OTHER2,
) {
    /** 空欄のままだとボタンが壊れて見えるので、既定値に戻す。 */
    fun fill(template: String): String = template
        .replace("{self}", self.ifBlank { DEFAULT_SELF })
        .replace("{other2}", other2.ifBlank { DEFAULT_OTHER2 })
        .replace("{other}", other.ifBlank { DEFAULT_OTHER })
}

const val DEFAULT_SELF = "ソラ"
const val DEFAULT_OTHER = "タナカ"
const val DEFAULT_OTHER2 = "スズキ"

/** 既定の声。九州そら / ノーマル。同梱している 2.vvm に入っている。 */
const val DEFAULT_STYLE_ID = 16

/** 起動時の入力欄。CONTACT 01 を差し込んだうえで一度だけ入れる。 */
const val DEFAULT_TEXT_TEMPLATE = "{other}サン、イクヨ"

const val SPEED_MIN = 0.5f
const val SPEED_MAX = 2.0f
const val SPEED_STEP = 0.05f
const val DEFAULT_SPEED = 1.35f

/** UI 上は整数で見せて、CORE の pitchScale へ線形に写す。 */
const val PITCH_MIN = -12
const val PITCH_MAX = 12
const val DEFAULT_PITCH = 3

const val GAP_MIN = 0
const val GAP_MAX = 700
const val GAP_STEP = 20
const val DEFAULT_GAP_MS = 220

/** UI の PITCH (-12〜+12) を CORE の pitchScale (-0.15〜+0.15) へ。 */
fun pitchScaleOf(uiPitch: Int): Double = uiPitch / 12.0 * 0.15

/** Android の TextToSpeech.setPitch は 0 以下を受け付けない。 */
const val TTS_PITCH_MIN = 0.1f

/** OS TTS へフォールバックしたときの pitch (0.1〜2.0)。 */
fun ttsPitchOf(uiPitch: Int): Float =
    (1.0 + uiPitch / 12.0 * 0.9).toFloat().coerceIn(TTS_PITCH_MIN, 2.0f)
