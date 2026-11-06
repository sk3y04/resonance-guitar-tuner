package dev.anima.resonance.music

import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Signed cents deviation between a measured frequency and a target.
 * Positive = measured above target (sharp), negative = below (flat).
 *
 * Deviations are NOT clamped: an octave detection error surfaces as
 * ±1200 cents so the state machine (not the math) can reject it.
 */
object Cents {

    private val LN2 = ln(2.0)

    /**
     * Nearest-cent deviation of [measuredHz] relative to [targetHz].
     * Throws [IllegalArgumentException] for non-positive frequencies.
     */
    fun deviation(measuredHz: Double, targetHz: Double): Int {
        require(measuredHz > 0.0) { "measuredHz must be > 0, got $measuredHz" }
        require(targetHz > 0.0) { "targetHz must be > 0, got $targetHz" }
        return (1200.0 * ln(measuredHz / targetHz) / LN2).roundToInt()
    }
}
