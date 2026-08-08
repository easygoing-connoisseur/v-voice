package com.example.voicetester

import android.content.Context
import android.util.Log
import jp.hiroshiba.voicevoxcore.blocking.Onnxruntime
import jp.hiroshiba.voicevoxcore.blocking.OpenJtalk
import jp.hiroshiba.voicevoxcore.blocking.Synthesizer
import jp.hiroshiba.voicevoxcore.blocking.VoiceModelFile
import java.io.File

/**
 * VOICEVOX CORE をアプリ内で直接動かす。ENGINE (localhost:50021) は使わないので通信は発生しない。
 *
 * CORE は辞書もモデルも実ファイルパスでしか受け取れないため
 * ([OpenJtalk] / [VoiceModelFile] のコンストラクタ)、初回起動時に assets から
 * 内部ストレージへ展開する必要がある。
 */
class VoicevoxController(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data class Extracting(val done: Int, val total: Int) : State
        data object Loading : State
        data class Ready(val initMs: Long) : State
        data class Failed(val message: String) : State
    }

    private var synthesizer: Synthesizer? = null
    private var openJtalk: OpenJtalk? = null

    /** 展開先。assets からコピーした辞書とモデルを置く。 */
    private val baseDir: File get() = File(context.filesDir, "voicevox")
    private val dictDir: File get() = File(baseDir, DICT_DIR)
    private val modelFile: File get() = File(baseDir, "$MODEL_DIR/$MODEL_NAME")

    /**
     * 展開と初期化。重いのでバックグラウンドスレッドから呼ぶこと。
     * 2 回目以降の起動では展開済みなので [onState] の Extracting は飛ばされる。
     */
    fun initialize(onState: (State) -> Unit) {
        val t0 = System.currentTimeMillis()
        try {
            extractAssets(onState)

            onState(State.Loading)
            // jniLibs に置いた libvoicevox_onnxruntime.so を既定のファイル名で拾わせる。
            val ort = Onnxruntime.loadOnce().perform()
            val jtalk = OpenJtalk(dictDir.absolutePath)
            openJtalk = jtalk

            // cpuNumThreads() は呼ばない。voicevoxcore 0.16.4 の Android AAR は
            // 判定が反転しており (ifeq)、u16 の範囲内＝妥当な値を渡すと必ず
            // IllegalArgumentException を投げる。既定のスレッド数に任せる。
            val synth = Synthesizer.builder(ort, jtalk).build()
            // close() すると finalize() が同じ Rust ポインタを二重解放して
            // "Null pointer in rust value from Java" を投げる。GC に任せる。
            val model = VoiceModelFile(modelFile.absolutePath)
            synth.loadVoiceModel(model)
            synthesizer = synth

            onState(State.Ready(System.currentTimeMillis() - t0))
        } catch (t: Throwable) {
            Log.e(TAG, "VOICEVOX init failed", t)
            onState(State.Failed(t.message ?: t.javaClass.simpleName))
        }
    }

    val isReady: Boolean get() = synthesizer != null

    /** 読み込んだモデルが持つスタイル一覧。(styleId, "キャラクター / スタイル") */
    fun styles(): List<Pair<Int, String>> {
        val synth = synthesizer ?: return emptyList()
        return synth.metas().flatMap { meta ->
            meta.styles.map { style -> style.id to "${meta.name} / ${style.name}" }
        }
    }

    fun characterName(styleId: Int): String? =
        synthesizer?.metas()?.firstOrNull { meta -> meta.styles.any { it.id == styleId } }?.name

    /**
     * 合成して WAV バイト列を返す。呼び出しは重いのでバックグラウンドから。
     *
     * @param pitch UI の -12〜+12 を CORE の pitchScale へ写した値
     */
    fun synthesize(
        text: String,
        styleId: Int,
        speed: Double,
        pitch: Double,
        intonation: Double,
        postPhonemeSec: Double?,
    ): ByteArray? {
        val synth = synthesizer ?: return null
        return try {
            val query = synth.createAudioQuery(text, styleId)
            query.speedScale = speed
            query.pitchScale = pitch
            query.intonationScale = intonation
            // null のときは CORE 既定の余韻を残す。0 にすると語尾が詰まる。
            postPhonemeSec?.let { query.postPhonemeLength = it }
            synth.synthesis(query, styleId).perform()
        } catch (t: Throwable) {
            Log.e(TAG, "synthesis failed", t)
            null
        }
    }

    /**
     * [Synthesizer] と [OpenJtalk] は close() を持たず finalize() で解放される。
     * 参照を捨てて GC に任せる。
     */
    fun close() {
        synthesizer = null
        openJtalk = null
    }

    /** assets から内部ストレージへコピーする。展開済みならスキップする。 */
    private fun extractAssets(onState: (State) -> Unit) {
        val entries = ASSET_FILES
        val marker = File(baseDir, ".extracted-$ASSET_VERSION")
        if (marker.exists()) return

        // 途中で落ちた残骸があると不完全なまま使ってしまうので作り直す。
        baseDir.deleteRecursively()
        dictDir.mkdirs()
        File(baseDir, MODEL_DIR).mkdirs()

        entries.forEachIndexed { index, name ->
            onState(State.Extracting(index, entries.size))
            val dest = File(baseDir, name)
            dest.parentFile?.mkdirs()
            context.assets.open("$ASSET_ROOT/$name").use { input ->
                dest.outputStream().buffered(BUFFER).use { output -> input.copyTo(output, BUFFER) }
            }
        }
        marker.writeText(ASSET_VERSION)
    }

    private companion object {
        const val TAG = "VoicevoxController"
        const val ASSET_ROOT = "voicevox"
        const val DICT_DIR = "dict"
        const val MODEL_DIR = "model"
        const val MODEL_NAME = "2.vvm"
        const val BUFFER = 1 shl 16

        /** 中身を差し替えたら上げる。上げると次回起動で展開がやり直される。 */
        const val ASSET_VERSION = "1"

        /** assets 配下の相対パス。AssetManager.list は遅いので直書きする。 */
        val ASSET_FILES = listOf(
            "$DICT_DIR/char.bin",
            "$DICT_DIR/COPYING",
            "$DICT_DIR/left-id.def",
            "$DICT_DIR/matrix.bin",
            "$DICT_DIR/pos-id.def",
            "$DICT_DIR/rewrite.def",
            "$DICT_DIR/right-id.def",
            "$DICT_DIR/sys.dic",
            "$DICT_DIR/unk.dic",
            "$MODEL_DIR/$MODEL_NAME",
        )
    }
}
