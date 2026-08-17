package com.example.voicetester

import android.content.Context

/**
 * 端末に残す設定。
 *
 * LOG は「メモリ内・終了で消去」という仕様なので含めない。
 * 入力中のテキストも、次回起動時は呼び名から作り直す方が自然なので保存しない。
 */
data class PersistedSettings(
    val identity: Identity = Identity(),
    val styleId: Int = DEFAULT_STYLE_ID,
    val speed: Float = DEFAULT_SPEED,
    val pitch: Int = DEFAULT_PITCH,
    val gapMs: Int = DEFAULT_GAP_MS,
    val intonationIndex: Int = 0,
    val quickCommands: List<String> = QUICK_COMMANDS,
)

/**
 * 設定の読み書き。保存する量が知れているので DataStore は入れず SharedPreferences で足す。
 *
 * 壊れた値や範囲外の値が入っていても既定値へ丸めて返す。
 * 設定ファイルは端末の他アプリからは触れないが、旧版が書いた値が残ることはある。
 */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): PersistedSettings {
        val d = PersistedSettings()
        return PersistedSettings(
            identity = Identity(
                self = prefs.getString(KEY_SELF, null) ?: d.identity.self,
                other = prefs.getString(KEY_OTHER, null) ?: d.identity.other,
                other2 = prefs.getString(KEY_OTHER2, null) ?: d.identity.other2,
            ),
            styleId = prefs.getInt(KEY_STYLE_ID, d.styleId),
            speed = prefs.getFloat(KEY_SPEED, d.speed).coerceIn(SPEED_MIN, SPEED_MAX),
            pitch = prefs.getInt(KEY_PITCH, d.pitch).coerceIn(PITCH_MIN, PITCH_MAX),
            gapMs = prefs.getInt(KEY_GAP_MS, d.gapMs).coerceIn(GAP_MIN, GAP_MAX),
            intonationIndex = prefs.getInt(KEY_INTONATION, d.intonationIndex)
                .coerceIn(INTONATIONS.indices),
            quickCommands = prefs.getString(KEY_QUICK, null)
                ?.let(::decodeQuick)
                ?.take(QUICK_MAX)
                ?: d.quickCommands,
        )
    }

    fun save(s: PersistedSettings) {
        prefs.edit()
            .putString(KEY_SELF, s.identity.self)
            .putString(KEY_OTHER, s.identity.other)
            .putString(KEY_OTHER2, s.identity.other2)
            .putInt(KEY_STYLE_ID, s.styleId)
            .putFloat(KEY_SPEED, s.speed)
            .putInt(KEY_PITCH, s.pitch)
            .putInt(KEY_GAP_MS, s.gapMs)
            .putInt(KEY_INTONATION, s.intonationIndex)
            .putString(KEY_QUICK, encodeQuick(s.quickCommands))
            .apply()
    }

    private companion object {
        const val FILE = "v-voice"

        const val KEY_SELF = "identity.self"
        const val KEY_OTHER = "identity.other"
        const val KEY_OTHER2 = "identity.other2"
        const val KEY_STYLE_ID = "voice.styleId"
        const val KEY_SPEED = "voice.speed"
        const val KEY_PITCH = "voice.pitch"
        const val KEY_GAP_MS = "voice.gapMs"
        const val KEY_INTONATION = "voice.intonation"
        const val KEY_QUICK = "quick.commands"
    }
}

/**
 * クイックコマンドは順序が意味を持つので putStringSet は使えない。
 * 入力欄が 1 行固定で改行を含みえないため、改行区切りの 1 本の文字列にして持つ。
 */
private const val QUICK_SEPARATOR = "\n"

private fun encodeQuick(list: List<String>): String = list.joinToString(QUICK_SEPARATOR)

/** 空文字は「1 件も無い」。split すると [""] になってしまうので分けて扱う。 */
private fun decodeQuick(raw: String): List<String> =
    if (raw.isEmpty()) emptyList() else raw.split(QUICK_SEPARATOR)
