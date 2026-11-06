package dev.anima.resonance.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process

/**
 * Direct AudioRecord backend for the [AudioInput] contract (Android).
 *
 * Status: COMPILE-VERIFIED (2026-11-06, first real Android
 * build — `./scripts/verify.sh` assembleDebug + lint green). One compile
 * fix was required: the `rate` ctor param is not visible in member
 * functions — `open()` now uses `sampleRate`. No device testing yet
 *
 * Design (per spec): raw mono PCM16, explicit buffer sizing, no allocation
 * beyond the chunk conversion, read errors surface as null (device lost) for
 * the orchestrator's bounded recovery, not as exceptions.
 *
 * Requires RECORD_AUDIO at the CALLER (Android shell); this class does not
 * check permissions (the shell enforces lifecycle + permission policy).
 */
@SuppressLint("MissingPermission")
class AndroidAudioInput(
    rate: Int = 48_000,
    private val chunkSize: Int = 1024,
) : AudioInput {

    override val sampleRate: Int = rate

    private val minBufferBytes: Int =
        AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

    private val bufferBytes: Int =
        maxOf(minBufferBytes, chunkSize * 4).coerceAtLeast(chunkSize * 2)

    private var record: AudioRecord? = null
    private var shortBuf: ShortArray = ShortArray(0)
    private var floatChunk: FloatArray = FloatArray(0)

    override fun open(): Boolean {
        if (record != null) return true // idempotent
        if (minBufferBytes <= 0) return false
        return try {
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return false
            }
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                rec.release()
                return false
            }
            record = rec
            shortBuf = ShortArray(chunkSize)
            floatChunk = FloatArray(chunkSize)
            true
        } catch (e: SecurityException) {
            false // no RECORD_AUDIO permission — caller surface handles it
        }
    }

    override fun readChunk(): FloatArray? {
        val rec = record ?: return null
        val n = rec.read(shortBuf, 0, shortBuf.size)
        if (n <= 0) return null // AUDIO_ERR / device lost -> orchestrator recovers
        for (i in 0 until n) {
            floatChunk[i] = shortBuf[i] / 32768.0f
        }
        return if (n == floatChunk.size) floatChunk else floatChunk.copyOf(n)
    }

    override fun close() {
        record?.let { rec ->
            try {
                rec.stop()
            } catch (e: IllegalStateException) {
                // already stopped — release regardless
            }
            rec.release()
        }
        record = null
    }

    companion object {
        /** Foreground app audio priority; kept for future tuning, not exposed. */
        const val DEFAULT_RATE: Int = 48_000
    }
}
