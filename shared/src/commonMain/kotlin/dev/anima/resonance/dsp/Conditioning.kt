package dev.anima.resonance.dsp

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Signal conditioning utilities. All functions are pure: none mutates the
 * caller's buffer (contract — verified by tests).
 */
object Conditioning {

    /** Returns a NEW array with the DC component (mean) removed. */
    fun removeDc(input: FloatArray): FloatArray {
        require(input.isNotEmpty()) { "input must not be empty" }
        var mean = 0.0
        for (x in input) mean += x.toDouble()
        mean /= input.size
        val meanF = mean.toFloat()
        return FloatArray(input.size) { input[it] - meanF }
    }

    /** Root-mean-square level of the window (0.0 for all-silence). */
    fun rmsLevel(input: FloatArray): Double {
        require(input.isNotEmpty()) { "input must not be empty" }
        var sum = 0.0
        for (x in input) {
            val v = x.toDouble()
            sum += v * v
        }
        return sqrt(sum / input.size)
    }

    /** Fraction of samples with |x| >= [threshold]; threshold must be in (0, 1]. */
    fun clippingRatio(input: FloatArray, threshold: Double = 0.99): Double {
        require(input.isNotEmpty()) { "input must not be empty" }
        require(threshold > 0.0 && threshold <= 1.0) { "threshold out of bounds (0,1]: $threshold" }
        var clipped = 0
        for (x in input) {
            if (abs(x.toDouble()) >= threshold) clipped++
        }
        return clipped / input.size.toDouble()
    }
}
