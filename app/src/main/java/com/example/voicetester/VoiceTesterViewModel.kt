package com.example.voicetester

import android.app.Application
import android.speech.tts.Voice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class Backend { VOICEVOX, OS_TTS }

enum class Status { BOOTING, READY, SYNTHESIZING, ACTIVE, FAILED }

data class LogEntry(
    val time: String,
    val text: String,
    val profile: String,
    val speed: Float,
    val styleId: Int,
    val pitch: Int,
    val intonationIndex: Int,
)

data class VVoiceState(
    val text: String = Identity().fill(DEFAULT_TEXT_TEMPLATE),
    val identity: Identity = Identity(),
    val backend: Backend = Backend.VOICEVOX,
    val status: Status = Status.BOOTING,
    val bootMessage: String = "",
    val styles: List<Pair<Int, String>> = emptyList(),
    val styleId: Int = DEFAULT_STYLE_ID,
    val speed: Float = DEFAULT_SPEED,
    val pitch: Int = DEFAULT_PITCH,
    val gapMs: Int = DEFAULT_GAP_MS,
    val intonationIndex: Int = 0,
    val quickCommands: List<String> = QUICK_COMMANDS,
    val logs: List<LogEntry> = emptyList(),
    val speakingText: String = "",
    val lastSynthMs: Long? = null,
    val initMs: Long? = null,
    val engineError: String? = null,
    // OS TTS フォールバック用
    val osVoices: List<Voice> = emptyList(),
) {
    val intonation: Intonation get() = INTONATIONS[intonationIndex.coerceIn(INTONATIONS.indices)]

    val profileLabel: String
        get() = styles.firstOrNull { it.first == styleId }?.second ?: "--"

    /** クレジット表記に使うキャラクター名。"九州そら / ノーマル" の左側。 */
    val characterName: String?
        get() = if (backend == Backend.VOICEVOX) {
            profileLabel.substringBefore(" / ").takeIf { it != "--" }
        } else {
            null
        }

    val isSpeaking: Boolean get() = status == Status.ACTIVE || status == Status.SYNTHESIZING

    val canSpeak: Boolean
        get() = text.isNotBlank() && when (backend) {
            Backend.VOICEVOX -> status != Status.BOOTING && status != Status.FAILED
            Backend.OS_TTS -> osVoices.isNotEmpty()
        }
}

class VoiceTesterViewModel(application: Application) : AndroidViewModel(application) {

    private val _ui = MutableStateFlow(VVoiceState())
    val ui: StateFlow<VVoiceState> = _ui.asStateFlow()

    private val voicevox = VoicevoxController(application)
    val player = AudioPlayer()

    /** 同じ文言・同じ設定なら合成し直さない。クイックコマンドの 2 回目以降が即応になる。 */
    private val cache = LinkedHashMap<String, ByteArray>()

    /** 停止や割り込みで捨てられた合成結果が、後から再生に割り込まないようにする。 */
    private var generation = 0L

    /**
     * OS TTS は VOICEVOX が使えないときだけのフォールバック。
     * 触るだけで Google TTS へのバインドとモデル読み込みが走るので、
     * 本当に必要になるまで生成しない。
     */
    private var tts: TtsController? = null

    private fun ensureTts(): TtsController = tts ?: newTts().also { tts = it }

    private fun newTts(): TtsController {
        return TtsController(
            context = getApplication(),
            onReady = { voices, _ -> _ui.update { it.copy(osVoices = voices) } },
            onFailed = { _ui.update { it.copy(engineError = "OS TTS init failed") } },
            onSpeakingChange = { speaking ->
                _ui.update {
                    if (it.backend == Backend.OS_TTS) {
                        it.copy(status = if (speaking) Status.ACTIVE else Status.READY)
                    } else {
                        it
                    }
                }
            },
        )
    }

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                voicevox.initialize { state -> onInitState(state) }
            }
        }
    }

    private fun onInitState(state: VoicevoxController.State) {
        when (state) {
            is VoicevoxController.State.Extracting ->
                _ui.update {
                    it.copy(
                        status = Status.BOOTING,
                        bootMessage = "EXTRACTING ${state.done + 1}/${state.total}",
                    )
                }

            VoicevoxController.State.Loading ->
                _ui.update { it.copy(status = Status.BOOTING, bootMessage = "LOADING MODEL") }

            is VoicevoxController.State.Ready -> {
                val styles = voicevox.styles()
                _ui.update {
                    it.copy(
                        status = Status.READY,
                        bootMessage = "",
                        styles = styles,
                        styleId = if (styles.any { s -> s.first == it.styleId }) {
                            it.styleId
                        } else {
                            styles.firstOrNull()?.first ?: it.styleId
                        },
                        initMs = state.initMs,
                    )
                }
            }

            is VoicevoxController.State.Failed -> {
                // CORE が使えないときだけ OS TTS へ落とす。アプリが無言になるのを避ける。
                ensureTts()
                _ui.update {
                    it.copy(
                        backend = Backend.OS_TTS,
                        status = Status.READY,
                        bootMessage = "",
                        engineError = state.message,
                    )
                }
            }

            VoicevoxController.State.Idle -> Unit
        }
    }

    fun onTextChange(value: String) = _ui.update { it.copy(text = value) }

    fun onIdentityChange(identity: Identity) = _ui.update { it.copy(identity = identity) }

    fun onStyleSelected(styleId: Int) = _ui.update { it.copy(styleId = styleId) }

    fun onSpeedChange(value: Float) {
        val snapped = snap(value, SPEED_STEP, SPEED_MIN, SPEED_MAX)
        _ui.update { it.copy(speed = snapped) }
    }

    fun onPitchChange(value: Float) =
        _ui.update { it.copy(pitch = value.roundToInt().coerceIn(PITCH_MIN, PITCH_MAX)) }

    fun onGapChange(value: Float) {
        val snapped = snap(value, GAP_STEP.toFloat(), GAP_MIN.toFloat(), GAP_MAX.toFloat())
        _ui.update { it.copy(gapMs = snapped.roundToInt()) }
    }

    fun cycleIntonation() =
        _ui.update { it.copy(intonationIndex = (it.intonationIndex + 1) % INTONATIONS.size) }

    /* ------------------------------------------------------ quick command */

    fun onQuickCommandChange(index: Int, value: String) = _ui.update {
        if (index !in it.quickCommands.indices) return@update it
        // 1 行のボタンに載せる文言なので、貼り付けなどで紛れ込んだ改行は潰す。
        val cleaned = value.replace('\n', ' ').replace('\r', ' ')
        it.copy(quickCommands = it.quickCommands.toMutableList().apply { this[index] = cleaned })
    }

    fun addQuickCommand() = _ui.update {
        if (it.quickCommands.size >= QUICK_MAX) it else it.copy(quickCommands = it.quickCommands + "")
    }

    fun removeQuickCommand(index: Int) = _ui.update {
        if (index !in it.quickCommands.indices) return@update it
        it.copy(quickCommands = it.quickCommands.filterIndexed { i, _ -> i != index })
    }

    fun resetQuickCommands() = _ui.update { it.copy(quickCommands = QUICK_COMMANDS) }

    /** SPEAK と QUICK の共通入口。再生中でも割り込んで即座に鳴らす。 */
    fun speak(text: String = _ui.value.text, setInput: Boolean = false, log: Boolean = true) {
        val body = text.trim()
        if (body.isEmpty()) return
        val state = _ui.value
        if (setInput) _ui.update { it.copy(text = body) }
        if (!state.canSpeak) return

        generation++
        val myGen = generation
        player.stop()
        tts?.stop()

        if (log) pushLog(body, state)

        if (state.backend == Backend.OS_TTS) {
            _ui.update { it.copy(status = Status.ACTIVE, speakingText = body) }
            ensureTts().speak(
                parts = chunkText(body, state.intonation.split),
                gapMs = state.gapMs.toLong(),
                pitch = ttsPitchOf(state.pitch),
                rate = state.speed,
                voice = state.osVoices.firstOrNull(),
            )
            return
        }

        val key = cacheKey(body, state)
        cache[key]?.let { cached ->
            startPlayback(cached, body, myGen)
            return
        }

        _ui.update { it.copy(status = Status.SYNTHESIZING, speakingText = body) }
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            val wav = withContext(Dispatchers.Default) {
                voicevox.synthesize(
                    text = body,
                    styleId = state.styleId,
                    speed = state.speed.toDouble(),
                    pitch = pitchScaleOf(state.pitch),
                    intonation = state.intonation.intonationScale,
                    // gap=0 のときは CORE 既定の余韻を残す。0 にすると語尾が詰まる。
                    postPhonemeSec = state.gapMs.takeIf { it > 0 }?.let { it / 1000.0 },
                )
            }
            if (myGen != generation) return@launch
            if (wav == null) {
                _ui.update { it.copy(status = Status.READY, speakingText = "") }
                return@launch
            }
            _ui.update { it.copy(lastSynthMs = System.currentTimeMillis() - t0) }
            if (cache.size >= CACHE_LIMIT) cache.remove(cache.keys.first())
            cache[key] = wav
            startPlayback(wav, body, myGen)
        }
    }

    private fun startPlayback(wav: ByteArray, body: String, myGen: Long) {
        if (myGen != generation) return
        _ui.update { it.copy(status = Status.ACTIVE, speakingText = body) }
        player.play(wav) {
            if (myGen == generation) {
                _ui.update { it.copy(status = Status.READY, speakingText = "") }
            }
        }
    }

    fun stop() {
        generation++
        player.stop()
        tts?.stop()
        _ui.update { it.copy(status = Status.READY, speakingText = "") }
    }

    /** ログから再生する。記録時の設定をそのまま使う。 */
    fun replay(entry: LogEntry) {
        _ui.update {
            it.copy(
                styleId = entry.styleId,
                speed = entry.speed,
                pitch = entry.pitch,
                intonationIndex = entry.intonationIndex,
            )
        }
        speak(entry.text, setInput = false, log = false)
    }

    private fun pushLog(text: String, state: VVoiceState) {
        val entry = LogEntry(
            time = TIME_FORMAT.format(Date()),
            text = text,
            profile = if (state.backend == Backend.VOICEVOX) state.profileLabel else "OS TTS",
            speed = state.speed,
            styleId = state.styleId,
            pitch = state.pitch,
            intonationIndex = state.intonationIndex,
        )
        _ui.update { it.copy(logs = (listOf(entry) + it.logs).take(LOG_LIMIT)) }
    }

    private fun cacheKey(text: String, s: VVoiceState) =
        listOf(text, s.styleId, s.speed, s.pitch, s.intonationIndex, s.gapMs).joinToString("|")

    private fun snap(value: Float, step: Float, min: Float, max: Float): Float =
        ((value / step).roundToInt() * step).coerceIn(min, max)

    override fun onCleared() {
        player.stop()
        tts?.shutdown()
        voicevox.close()
        super.onCleared()
    }

    private companion object {
        const val LOG_LIMIT = 50
        const val CACHE_LIMIT = 32
        val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)
    }
}
