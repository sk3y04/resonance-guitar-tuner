package dev.anima.resonance.audio

/**
 * Capture orchestration. Owns start/stop idempotence, chunk
 * delivery, and BOUNDED recovery from transient device errors.
 *
 * Deterministic: the caller drives time (nowMs) and the pump (poll), so the
 * whole lifecycle is unit-testable with a fake [AudioInput] and a virtual
 * clock — no threads, no sleeps.
 */
class CaptureOrchestrator(
    private val input: AudioInput,
    private val onChunk: (FloatArray, Long) -> Unit,
    private val onState: (CaptureState) -> Unit,
    private val maxConsecutiveErrors: Int = 3,
) {
    var state: CaptureState = CaptureState.IDLE
        private set

    private var consecutiveErrors = 0

    /** Opens capture. Idempotent: repeated calls while capturing are no-ops. */
    fun start(): Unit {
        if (state == CaptureState.CAPTURING || state == CaptureState.RECOVERING) return
        consecutiveErrors = 0
        if (input.open()) {
            setState(CaptureState.CAPTURING)
        } else {
            // Failed open is NOT closed (nothing was acquired). Retry is a
            // fresh user-driven start().
            setState(CaptureState.ERROR)
        }
    }

    /** Stops and releases capture. Idempotent. Safe from any state. */
    fun stop(): Unit {
        if (state == CaptureState.CAPTURING || state == CaptureState.RECOVERING) {
            input.close()
        }
        if (state != CaptureState.IDLE) {
            setState(CaptureState.IDLE)
        }
    }

    /**
     * One pump step at virtual time [nowMs]: reads one chunk and delivers it,
     * or records a device error. After [maxConsecutiveErrors] consecutive
     * errors the capture stops itself and reports ERROR (bounded recovery —
     * no infinite retry loop, no unbounded work).
     */
    fun poll(nowMs: Long): Unit {
        if (state != CaptureState.CAPTURING && state != CaptureState.RECOVERING) return
        val chunk = input.readChunk()
        if (chunk != null && chunk.isNotEmpty()) {
            if (state == CaptureState.RECOVERING) setState(CaptureState.CAPTURING)
            consecutiveErrors = 0
            onChunk(chunk, nowMs)
        } else {
            consecutiveErrors++
            if (consecutiveErrors >= maxConsecutiveErrors) {
                input.close()
                setState(CaptureState.ERROR)
            } else {
                setState(CaptureState.RECOVERING)
            }
        }
    }

    private fun setState(s: CaptureState) {
        if (state != s) {
            state = s
            onState(s)
        }
    }
}
