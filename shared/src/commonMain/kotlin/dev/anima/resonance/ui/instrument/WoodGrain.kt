package dev.anima.resonance.ui.instrument

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Deterministic procedural wood grain (no Compose imports).
 *
 * The enclosure's grain is generated once per panel size from a fixed seed
 * and cached by the renderer — it must never shimmer or regenerate while
 * the needle moves. Streaks run vertically (along the panel's long axis)
 * with a gentle sinusoidal sideways wander, like quarter-sawn walnut.
 */
object WoodGrain {

    /**
     * One grain streak: a vertical line starting at [startX] that wanders
     * sideways by up to [amplitude] px with the given [wavelength]/[phase].
     * [thickness] is the stroke width in px, [alpha] its opacity (0..1).
     */
    data class Streak(
        val startX: Float,
        val amplitude: Float,
        val wavelength: Float,
        val phase: Float,
        val thickness: Float,
        val alpha: Float,
    )

    /**
     * Deterministic streaks for a [width] x [height] panel. The same seed
     * and size always return the same list; a tiny LCG keeps this free of
     * platform Random seeding differences.
     */
    fun streaks(seed: Long, width: Float, height: Float, count: Int = 26): List<Streak> {
        var state = seed * 6364136223846793005L + 1442695040888963407L
        fun next(): Double {
            state = state * 6364136223846793005L + 1442695040888963407L
            // Take the high bits; map to [0, 1).
            return ((state ushr 11).toDouble() / (1L shl 53).toDouble())
        }

        val w = max(width, 1f)
        val h = max(height, 1f)
        return List(count) {
            val startX = (next() * w).toFloat()
            val amplitude = (next() * w * 0.035).toFloat()
            val wavelength = (h * (0.35 + next() * 0.9)).toFloat()
            val phase = (next() * 2.0 * PI).toFloat()
            val thickness = (0.6 + next() * 1.8).toFloat()
            val alpha = (0.05 + next() * 0.12).toFloat()
            Streak(startX, amplitude, wavelength, phase, thickness, alpha)
        }
    }

    /** Sideways offset of a streak at vertical position [y] (px). */
    fun wanderAt(streak: Streak, y: Float): Float =
        (streak.amplitude * sin(2.0 * PI * y / streak.wavelength + streak.phase)).toFloat()
}