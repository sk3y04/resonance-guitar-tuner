package dev.anima.resonance.ui.instrument

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Deterministic procedural walnut grain (no Compose imports).
 *
 * The enclosure's figure is generated once per panel size from a fixed
 * seed and cached by the renderer — it must never shimmer or regenerate
 * while the needle moves. Grain runs HORIZONTALLY (flat-sawn walnut figure)
 * with a gentle vertical wander; broad [bands] add natural tonal variation
 * so the surface reads as a material, not a flat fill or noise.
 */
object WoodGrain {

    /**
     * One horizontal grain streak: a line at [startY] running across the
     * panel that wanders vertically by up to [amplitude] px with the given
     * [wavelength]/[phase]. [thickness] is the stroke width in px, [alpha]
     * its opacity (0..1).
     */
    data class Streak(
        val startY: Float,
        val amplitude: Float,
        val wavelength: Float,
        val phase: Float,
        val thickness: Float,
        val alpha: Float,
    )

    /**
     * A broad, soft tonal band (lighter or darker heartwood) centered at
     * [centerY] with [halfHeight] px extent. [lighten] selects the tone;
     * [alpha] stays subtle (<= 0.2).
     */
    data class Band(
        val centerY: Float,
        val halfHeight: Float,
        val lighten: Boolean,
        val alpha: Float,
    )

    private class Lcg(seed: Long) {
        private var state = seed * 6364136223846793005L + 1442695040888963407L
        fun next(): Double {
            state = state * 6364136223846793005L + 1442695040888963407L
            return ((state ushr 11).toDouble() / (1L shl 53).toDouble())
        }
    }

    /**
     * Deterministic horizontal streaks for a [width] x [height] panel. The
     * same seed and size always return the same list.
     */
    fun streaks(seed: Long, width: Float, height: Float, count: Int = 30): List<Streak> {
        val rnd = Lcg(seed)
        val w = max(width, 1f)
        val h = max(height, 1f)
        return List(count) {
            val startY = (rnd.next() * h).toFloat()
            val amplitude = (rnd.next() * h * 0.022).toFloat()
            val wavelength = (w * (0.5 + rnd.next() * 1.3)).toFloat()
            val phase = (rnd.next() * 2.0 * PI).toFloat()
            val thickness = (0.6 + rnd.next() * 2.0).toFloat()
            val alpha = (0.07 + rnd.next() * 0.13).toFloat()
            Streak(startY, amplitude, wavelength, phase, thickness, alpha)
        }
    }

    /** Deterministic broad tonal bands for a panel of the given [height]. */
    fun bands(seed: Long, height: Float, count: Int = 5): List<Band> {
        // Different stream than the streaks so bands don't mirror them.
        val rnd = Lcg(seed xor 0x5DEECE66DL)
        val h = max(height, 1f)
        return List(count) {
            val centerY = (rnd.next() * h).toFloat()
            val halfHeight = (h * (0.06 + rnd.next() * 0.12)).toFloat()
            val lighten = rnd.next() > 0.5
            val alpha = (0.05 + rnd.next() * 0.08).toFloat()
            Band(centerY, halfHeight, lighten, alpha)
        }
    }

    /** Vertical offset of a streak at horizontal position [x] (px). */
    fun wanderAt(streak: Streak, x: Float): Float =
        (streak.amplitude * sin(2.0 * PI * x / streak.wavelength + streak.phase)).toFloat()
}