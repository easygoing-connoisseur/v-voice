package com.example.voicetester

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * [TextToSpeech] の薄いラッパ。
 *
 * HTML 版が `onend` + `setTimeout` で次のチャンクを鳴らしていたところは、
 * [TextToSpeech.playSilentUtterance] で無音をキューに挟む形に置き換えている。
 * 全チャンクを一度に投入できるので、逐次コールバックのチェーンが要らない。
 *
 * コールバックはメインスレッド以外から来る。呼び出し側はスレッドセーフな
 * 入れ物（StateFlow など）で受けること。
 */
class TtsController(
    context: Context,
    private val onReady: (voices: List<Voice>, japaneseAvailable: Boolean) -> Unit,
    private val onFailed: () -> Unit,
    private val onSpeakingChange: (Boolean) -> Unit,
) {
    private var tts: TextToSpeech? = null

    /**
     * 発話のたびに増やす。停止や再発話で捨てられた古い utterance のコールバックが
     * 遅れて届いても、現在の発話状態を巻き込まないようにするための世代番号。
     */
    @Volatile
    private var session = 0L

    @Volatile
    private var firstUtteranceId: String? = null

    @Volatile
    private var lastUtteranceId: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) configure() else onFailed()
        }
    }

    private fun configure() {
        val engine = tts ?: return

        val langResult = engine.setLanguage(Locale.JAPANESE)
        val languageOk = langResult != TextToSpeech.LANG_MISSING_DATA &&
            langResult != TextToSpeech.LANG_NOT_SUPPORTED

        // エンジンによっては voices が null を返したり例外を投げたりする。
        val all = runCatching { engine.voices?.toList() }.getOrNull().orEmpty()
        val japanese = all.filter { it.locale.language == Locale.JAPANESE.language }
        // HTML 版と同じフォールバック: 日本語音声が 0 件なら全件を出す。
        val list = (if (japanese.isNotEmpty()) japanese else all).sortedBy { it.name }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == firstUtteranceId) onSpeakingChange(true)
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == lastUtteranceId) onSpeakingChange(false)
            }

            @Deprecated("API 21 で errorCode 付きに置き換えられたが、抽象メソッドなので実装が要る")
            override fun onError(utteranceId: String?) {
                if (utteranceId.isCurrentSession()) onSpeakingChange(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
        })

        onReady(list, languageOk && japanese.isNotEmpty())
    }

    private fun String?.isCurrentSession() = this != null && startsWith(sessionPrefix())

    private fun sessionPrefix() = "u$session-"

    /**
     * [parts] を順に発話し、チャンクの間に [gapMs] の無音を挟む。
     *
     * pitch / rate / voice はエンジン全体の設定なので、キュー投入の前に一度だけ入れる。
     */
    fun speak(parts: List<String>, gapMs: Long, pitch: Float, rate: Float, voice: Voice?) {
        val engine = tts ?: return
        val chunks = parts.enforceMaxLength()
        if (chunks.isEmpty()) return

        voice?.let { engine.voice = it }
        // Android の setPitch は 0 以下を受け付けない。
        engine.setPitch(pitch.coerceAtLeast(TTS_PITCH_MIN))
        engine.setSpeechRate(rate)

        session++
        val prefix = sessionPrefix()
        firstUtteranceId = "${prefix}0"
        lastUtteranceId = "$prefix${chunks.lastIndex}"

        chunks.forEachIndexed { index, part ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(part, mode, null, "$prefix$index")
            if (gapMs > 0 && index < chunks.lastIndex) {
                engine.playSilentUtterance(gapMs, TextToSpeech.QUEUE_ADD, "g$session-$index")
            }
        }

        // onStart を待たずに UI を発話中にして、停止ボタンをすぐ押せるようにする。
        onSpeakingChange(true)
    }

    fun stop() {
        // 世代を進めて、フラッシュされた utterance の遅延コールバックを無視させる。
        session++
        firstUtteranceId = null
        lastUtteranceId = null
        tts?.stop()
        onSpeakingChange(false)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    /** エンジンの 1 発話あたりの上限を超える長文が来ても落ちないようにする保険。 */
    private fun List<String>.enforceMaxLength(): List<String> {
        val max = TextToSpeech.getMaxSpeechInputLength()
        return if (all { it.length <= max }) this else flatMap { it.chunked(max) }
    }
}
