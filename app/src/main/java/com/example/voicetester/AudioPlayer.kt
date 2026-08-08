package com.example.voicetester

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max

/**
 * VOICEVOX が返す WAV を [AudioTrack] で鳴らす。
 *
 * MediaPlayer だと一時ファイルの書き出しが要るうえ再生位置の粒度が粗い。
 * PCM を直接流すことで、波形表示に使える再生位置がフレーム単位で取れる。
 */
class AudioPlayer {

    /** 波形表示用に間引いたピーク値 (0f..1f)。 */
    @Volatile
    var envelope: FloatArray = FloatArray(0)
        private set

    /** 再生位置 0f..1f。 */
    @Volatile
    var progress: Float = 0f
        private set

    @Volatile
    private var track: AudioTrack? = null

    /** 停止や再生し直しで捨てられた古いスレッドの後始末を無視するための世代番号。 */
    @Volatile
    private var generation = 0L

    val isPlaying: Boolean get() = track != null

    /**
     * [wav] を再生する。既に再生中なら中断して差し替える。
     * [onFinished] は再生完了・中断のどちらでも呼ばれる（中断時は最新世代のみ）。
     */
    fun play(wav: ByteArray, onFinished: () -> Unit) {
        stop()
        val pcm = parseWav(wav) ?: run {
            onFinished()
            return
        }

        generation++
        val myGen = generation
        envelope = buildEnvelope(pcm.samples)
        progress = 0f

        val channelMask =
            if (pcm.channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minBuffer = AudioTrack.getMinBufferSize(
            pcm.sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(BUFFER_FLOOR)

        val at = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(pcm.sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = at
        at.play()

        thread(name = "vvoice-audio") {
            val total = pcm.samples.size
            var offset = 0
            try {
                while (offset < total && generation == myGen) {
                    val count = minOf(CHUNK_SAMPLES, total - offset)
                    val written = at.write(pcm.samples, offset, count, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) break
                    offset += written
                    progress = offset.toFloat() / total
                }
                if (generation == myGen) {
                    // 書き込み完了 = 再生完了ではない。バッファが掃けるまで待つ。
                    runCatching { at.stop() }
                    progress = 1f
                }
            } catch (t: Throwable) {
                Log.e(TAG, "playback failed", t)
            } finally {
                runCatching { at.release() }
                if (generation == myGen) {
                    track = null
                    progress = 0f
                    onFinished()
                }
            }
        }
    }

    fun stop() {
        generation++
        val at = track ?: return
        track = null
        progress = 0f
        runCatching { at.pause() }
        runCatching { at.flush() }
        // release はスレッド側の finally に任せる（二重 release を避ける）。
    }

    private class Pcm(val samples: ShortArray, val sampleRate: Int, val channels: Int)

    /**
     * WAV から PCM を取り出す。ヘッダ長は 44 固定ではないので data チャンクを探す。
     * VOICEVOX の出力は 16bit PCM だが、想定外の形式なら null を返す。
     */
    private fun parseWav(wav: ByteArray): Pcm? {
        if (wav.size < 44) return null
        val bb = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        if (String(wav, 0, 4, Charsets.US_ASCII) != "RIFF") return null
        if (String(wav, 8, 4, Charsets.US_ASCII) != "WAVE") return null

        var pos = 12
        var sampleRate = 24000
        var channels = 1
        var bits = 16

        while (pos + 8 <= wav.size) {
            val id = String(wav, pos, 4, Charsets.US_ASCII)
            val size = bb.getInt(pos + 4)
            val body = pos + 8
            if (size < 0 || body + size > wav.size) break

            when (id) {
                "fmt " -> {
                    channels = bb.getShort(body + 2).toInt()
                    sampleRate = bb.getInt(body + 4)
                    bits = bb.getShort(body + 14).toInt()
                }

                "data" -> {
                    if (bits != 16) {
                        Log.e(TAG, "unsupported bit depth: $bits")
                        return null
                    }
                    val samples = ShortArray(size / 2)
                    ByteBuffer.wrap(wav, body, size).order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer().get(samples)
                    return Pcm(samples, sampleRate, channels.coerceAtLeast(1))
                }
            }
            // チャンクは 2 バイト境界に揃う。
            pos = body + size + (size and 1)
        }
        return null
    }

    /** 波形表示のために、一定サンプル数ごとのピークへ間引く。 */
    private fun buildEnvelope(samples: ShortArray): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)
        val buckets = (samples.size / ENVELOPE_STEP).coerceAtLeast(1)
        val out = FloatArray(buckets)
        for (i in 0 until buckets) {
            val start = i * ENVELOPE_STEP
            val end = minOf(start + ENVELOPE_STEP, samples.size)
            var peak = 0
            for (j in start until end) peak = max(peak, abs(samples[j].toInt()))
            out[i] = (peak / 32768f).coerceIn(0f, 1f)
        }
        return out
    }

    private companion object {
        const val TAG = "AudioPlayer"
        const val BUFFER_FLOOR = 4096
        const val CHUNK_SAMPLES = 2048
        /** 24kHz なら 1 バケット = 約 4ms。 */
        const val ENVELOPE_STEP = 96
    }
}
