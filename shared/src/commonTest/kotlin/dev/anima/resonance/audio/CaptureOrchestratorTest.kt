package dev.anima.resonance.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaptureOrchestratorTest {

    /** Scripted fake: replays queued chunks; null entries = device-lost reads. */
    private class FakeInput(
        var chunks: ArrayDeque<FloatArray?> = ArrayDeque(),
        var openResult: Boolean = true,
        override val sampleRate: Int = 48000,
    ) : AudioInput {
        var openCount = 0
        var closeCount = 0
        var lastChunkSize = -1

        override fun open(): Boolean {
            openCount++
            return openResult
        }

        override fun readChunk(): FloatArray? {
            val c = chunks.removeFirstOrNull() ?: FloatArray(0)
            lastChunkSize = c.size
            return c
        }

        override fun close() {
            closeCount++
        }
    }

    private class Harness(val openResult: Boolean = true) {
        val input = FakeInput(openResult = openResult)
        val chunks = mutableListOf<FloatArray>()
        val states = mutableListOf<CaptureState>()
        var lastTime = -1L
        val orch = CaptureOrchestrator(
            input = input,
            onChunk = { c, t -> chunks += c; lastTime = t },
            onState = { states += it },
        )
    }

    private fun chunk(v: Float = 0.1f, n: Int = 100) = FloatArray(n) { v }

    @Test
    fun startIsIdempotent() {
        val h = Harness()
        h.orch.start()
        h.orch.start()
        assertEquals(1, h.input.openCount, "open must happen exactly once")
        assertEquals(CaptureState.CAPTURING, h.orch.state)
    }

    @Test
    fun stopIsIdempotentAndReleases() {
        val h = Harness()
        h.orch.start()
        h.orch.stop()
        h.orch.stop()
        assertEquals(1, h.input.closeCount, "close must happen exactly once")
        assertEquals(CaptureState.IDLE, h.orch.state)
        assertEquals(0, h.chunks.size)
    }

    @Test
    fun pollDeliversChunks() {
        val h = Harness()
        h.input.chunks += chunk(0.2f)
        h.input.chunks += chunk(0.3f)
        h.orch.start()
        h.orch.poll(0L)
        h.orch.poll(21L)
        assertEquals(2, h.chunks.size)
        assertEquals(0.2f, h.chunks[0][0])
        assertEquals(0.3f, h.chunks[1][0])
        assertEquals(21L, h.lastTime, "virtual time must be passed through")
        assertEquals(CaptureState.CAPTURING, h.orch.state)
    }

    @Test
    fun transientErrorsEnterRecoveringThenRecover() {
        val h = Harness()
        h.input.chunks += null
        h.input.chunks += chunk(0.1f)
        h.orch.start()
        h.orch.poll(0L)
        assertEquals(CaptureState.RECOVERING, h.orch.state, "one error -> RECOVERING")
        h.orch.poll(21L)
        assertEquals(CaptureState.CAPTURING, h.orch.state, "good read -> back to CAPTURING")
        assertEquals(1, h.chunks.size)
    }

    @Test
    fun consecutiveErrorsBeyondBudgetStopCapture() {
        val h = Harness()
        h.orch.start()
        h.orch.poll(0L)   // null
        h.orch.poll(21L)  // null
        h.orch.poll(42L)  // null -> budget (3) exhausted
        assertEquals(CaptureState.ERROR, h.orch.state)
        assertTrue(h.input.closeCount >= 1, "capture must release the device")
        // bounded: further polls are no-ops, no re-open, no more closes
        val closesAfter = h.input.closeCount
        h.orch.poll(63L)
        assertEquals(closesAfter, h.input.closeCount)
        assertEquals(1, h.input.openCount)
    }

    @Test
    fun failedOpenIsErrorWithoutRelease() {
        val h = Harness(openResult = false)
        h.orch.start()
        assertEquals(CaptureState.ERROR, h.orch.state)
        assertEquals(0, h.input.closeCount, "a failed open must not be closed")
        // user-driven retry is a fresh start
        h.input.openResult = true
        h.orch.start()
        assertEquals(CaptureState.CAPTURING, h.orch.state)
        assertEquals(2, h.input.openCount)
    }

    @Test
    fun restartAfterStopReopensCleanly() {
        val h = Harness()
        h.orch.start()
        h.orch.stop()
        h.orch.start()
        assertEquals(2, h.input.openCount, "restart must create a fresh stream")
        assertEquals(1, h.input.closeCount)
        assertEquals(CaptureState.CAPTURING, h.orch.state)
    }

    @Test
    fun pollWhileIdleIsNoOp() {
        val h = Harness()
        h.orch.poll(0L)
        assertEquals(0, h.chunks.size)
        assertEquals(CaptureState.IDLE, h.orch.state)
        assertEquals(0, h.input.openCount)
    }
}
