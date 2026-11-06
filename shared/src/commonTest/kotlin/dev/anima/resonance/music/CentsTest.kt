package dev.anima.resonance.music

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CentsTest {

    @Test
    fun zeroAtTarget() {
        assertEquals(0, Cents.deviation(440.0, 440.0))
        assertEquals(0, Cents.deviation(82.4069, 82.4069))
    }

    @Test
    fun signedHalfCentDeviation() {
        val sharp = 440.0 * twoTo(50.0 / 1200.0)   // 50 cents above
        val flat = 440.0 * twoTo(-50.0 / 1200.0)   // 50 cents below
        assertEquals(50, Cents.deviation(sharp, 440.0))
        assertEquals(-50, Cents.deviation(flat, 440.0))
    }

    @Test
    fun smallDeviation() {
        assertEquals(3, Cents.deviation(440.0 * twoTo(3.0 / 1200.0), 440.0))
        assertEquals(-3, Cents.deviation(440.0 * twoTo(-3.0 / 1200.0), 440.0))
    }

    @Test
    fun octaveErrorIsReportedWithFullSignedRange() {
        // An octave-detection error must surface as ±1200 cents, never clamped.
        assertEquals(1200, Cents.deviation(880.0, 440.0))
        assertEquals(-1200, Cents.deviation(440.0, 880.0))
    }

    @Test
    fun rejectsNonPositiveFrequencies() {
        assertFailsWith<IllegalArgumentException> { Cents.deviation(0.0, 440.0) }
        assertFailsWith<IllegalArgumentException> { Cents.deviation(440.0, -1.0) }
    }

    private fun twoTo(x: Double): Double = 2.0.pow(x)
}
