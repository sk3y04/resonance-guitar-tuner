package dev.anima.resonance.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameAssemblerTest {

    @Test
    fun emitsWindowOnceEnoughSamplesArrived() {
        val asmb = FrameAssembler(windowSize = 4)
        asmb.push(floatArrayOf(1.0f, 2.0f))
        assertFalse(asmb.isReady())
        asmb.push(floatArrayOf(3.0f, 4.0f))
        assertTrue(asmb.isReady())
        assertTrue(floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f).contentEquals(asmb.takeFrame()))
    }

    @Test
    fun slidingWindowKeepsMostRecentSamples() {
        val asmb = FrameAssembler(windowSize = 3)
        asmb.push(floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f))
        assertTrue(floatArrayOf(3.0f, 4.0f, 5.0f).contentEquals(asmb.takeFrame()))
    }

    @Test
    fun streamingEquivalenceSampleBySampleVsBulk() {
        val bulk = (0 until 10).map { (it * 7 % 11) / 10.0f }
        val bySample = FrameAssembler(4)
        bulk.forEach { bySample.push(floatArrayOf(it)) }
        val byBulk = FrameAssembler(4)
        byBulk.push(bulk.toFloatArray())
        assertTrue(byBulk.takeFrame().contentEquals(bySample.takeFrame()))
    }

    @Test
    fun pushDoesNotMutateCallerBuffer() {
        val chunk = floatArrayOf(0.1f, 0.2f, 0.3f)
        val before = chunk.copyOf()
        val asmb = FrameAssembler(2)
        asmb.push(chunk)
        assertTrue(before.contentEquals(chunk), "pushed buffer must not be mutated")
    }

    @Test
    fun takeFrameBeforeReadyIsRejected() {
        val asmb = FrameAssembler(8)
        asmb.push(floatArrayOf(1.0f, 2.0f))
        assertFailsWith<IllegalStateException> { asmb.takeFrame() }
    }

    @Test
    fun emptyPushIsNoOp() {
        val asmb = FrameAssembler(2)
        asmb.push(FloatArray(0))
        assertFalse(asmb.isReady())
    }

    @Test
    fun rejectsNonPositiveWindowSize() {
        assertFailsWith<IllegalArgumentException> { FrameAssembler(0) }
    }
}
