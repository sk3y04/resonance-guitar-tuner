package dev.anima.resonance.dsp

/**
 * Bounded ring assembler that accumulates streaming samples into the most
 * recent [windowSize]-sample frame. Never mutates caller buffers (pushed
 * contents are copied into the ring).
 */
class FrameAssembler(val windowSize: Int) {

    init {
        require(windowSize > 0) { "windowSize must be > 0" }
    }

    private val ring = FloatArray(windowSize)
    private var count = 0
    private var write = 0

    /** Appends [samples] to the ring (contents copied; caller buffer untouched). */
    fun push(samples: FloatArray) {
        if (samples.isEmpty()) return
        for (s in samples) {
            ring[write] = s
            write = (write + 1) % windowSize
            if (count < windowSize) count++
        }
    }

    /** True once at least [windowSize] samples have been pushed. */
    fun isReady(): Boolean = count >= windowSize

    /** Returns a COPY of the most recent [windowSize] samples, oldest first. */
    fun takeFrame(): FloatArray {
        if (!isReady()) {
            throw IllegalStateException("not enough samples: $count < $windowSize")
        }
        val out = FloatArray(windowSize)
        System.arraycopy(ring, write, out, 0, windowSize - write)
        System.arraycopy(ring, 0, out, windowSize - write, write)
        return out
    }
}
