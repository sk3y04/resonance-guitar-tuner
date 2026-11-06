package dev.anima.resonance.audio

/**
 * Platform capture backend contract. Implementations: Android AudioRecord
 * (androidMain), future AVAudioEngine (iOS). Raw mono PCM floats in [-1, 1].
 * The production live-capture path never uses fakes; fakes live in tests.
 */
interface AudioInput {
    val sampleRate: Int

    /** Opens the device. Returns false when the device is unavailable. */
    fun open(): Boolean

    /** Reads the next chunk, or null when the device was lost. */
    fun readChunk(): FloatArray?

    /** Releases the device. Must be idempotent. */
    fun close()
}

/** Coarse capture state of the orchestrator. */
enum class CaptureState {
    IDLE,       // not capturing (fresh or stopped)
    CAPTURING,  // device open, chunks flowing
    RECOVERING, // transient device errors within the recovery budget
    ERROR,      // recovery budget exhausted / start failed — retry is user-driven
}
