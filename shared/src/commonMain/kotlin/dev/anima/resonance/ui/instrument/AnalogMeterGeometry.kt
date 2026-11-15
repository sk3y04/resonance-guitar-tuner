package dev.anima.resonance.ui.instrument

import dev.anima.resonance.presentation.MainScreenUi
import kotlin.math.abs

/**
 * Pure geometry for the analog tuning meter (no Compose imports).
 *
 * The meter is a classic moving-coil instrument face: the needle pivots
 * from a hub at the bottom center and sweeps symmetrically around vertical.
 * [needleAngle] returns degrees from 12 o'clock — negative toward FLAT
 * (left), positive toward SHARP (right). The source of truth is the same
 * normalized position the tested [MainScreenUi] produces (-1..1 over
 * ±[FULL_SCALE_CENTS]); visual travel is clamped at the mechanical stops
 * without altering any pitch math.
 */
object AnalogMeterGeometry {

    /** Cents at either mechanical stop (mirrors the tested UI contract). */
    const val FULL_SCALE_CENTS: Double = MainScreenUi.METER_FULL_SCALE_CENTS

    /** Total needle travel in degrees (±half around vertical). */
    const val SWEEP_DEGREES: Double = 90.0

    private const val HALF_SWEEP = SWEEP_DEGREES / 2.0

    /**
     * Rest position with no reliable reading: the flat-side stop, like a
     * real meter at rest. Never the center — a centered needle would claim
     * "in tune" while nothing is being measured.
     */
    const val REST_ANGLE: Double = -HALF_SWEEP

    /** Needle angle (degrees from vertical) for a normalized position. */
    fun needleAngle(position: Double): Double =
        (position.coerceIn(-1.0, 1.0) * HALF_SWEEP)

    /** Needle angle for a raw signed cents deviation (clamped at ±full scale). */
    fun needleAngleForCents(cents: Int): Double =
        needleAngle(cents / FULL_SCALE_CENTS)

    /** True when the deviation exceeds full scale (needle pinned at a stop). */
    fun isClamped(cents: Int): Boolean = abs(cents) > FULL_SCALE_CENTS

    /** Angle of the tick for [cents] on the same scale as the needle. */
    fun tickAngle(cents: Int): Double = needleAngleForCents(cents)

    /**
     * Half-width (degrees) of the painted in-tune band for a tolerance in
     * cents. The screen passes the state machine's exit tolerance (±8¢) so
     * the band matches exactly when "In tune" can be claimed.
     */
    fun inTuneBandHalfAngle(toleranceCents: Double): Double =
        (toleranceCents / FULL_SCALE_CENTS) * HALF_SWEEP
}