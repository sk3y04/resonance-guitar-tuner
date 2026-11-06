package dev.anima.resonance.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConditioningTest {

    @Test
    fun removeDcSubtractsMean() {
        val input = floatArrayOf(1.0f, -1.0f, 2.0f, -2.0f, 3.0f) // mean = 0.6
        val out = Conditioning.removeDc(input)
        val expected = input.map { (it - 0.6f).toDouble() }
        assertEquals(expected.size, out.size)
        expected.forEachIndexed { i, e -> assertEquals(e, out[i].toDouble(), 1e-6) }
    }

    @Test
    fun removeDcDoesNotMutateCallerBuffer() {
        val input = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val before = input.copyOf()
        Conditioning.removeDc(input)
        assertTrue(before.contentEquals(input), "caller buffer must not be mutated")
    }

    @Test
    fun rmsLevelOfKnownSignal() {
        val input = floatArrayOf(1.0f, -1.0f, 1.0f, -1.0f)
        assertEquals(1.0, Conditioning.rmsLevel(input), 1e-9)
        assertEquals(0.0, Conditioning.rmsLevel(FloatArray(8)), 1e-12)
    }

    @Test
    fun clippingRatio() {
        val input = floatArrayOf(0.5f, 0.995f, -0.999f, 0.1f, 1.0f)
        assertEquals(3.0 / 5.0, Conditioning.clippingRatio(input, 0.99), 1e-9)
        assertEquals(0.0, Conditioning.clippingRatio(floatArrayOf(0.1f, -0.1f), 0.99), 1e-12)
    }

    @Test
    fun rejectsEmptyInput() {
        assertFailsWith<IllegalArgumentException> { Conditioning.removeDc(FloatArray(0)) }
        assertFailsWith<IllegalArgumentException> { Conditioning.rmsLevel(FloatArray(0)) }
        assertFailsWith<IllegalArgumentException> { Conditioning.clippingRatio(FloatArray(0)) }
    }

    @Test
    fun rejectsNonsenseThreshold() {
        val input = floatArrayOf(0.5f)
        assertFailsWith<IllegalArgumentException> { Conditioning.clippingRatio(input, 0.0) }
        assertFailsWith<IllegalArgumentException> { Conditioning.clippingRatio(input, 2.0) }
    }
}
