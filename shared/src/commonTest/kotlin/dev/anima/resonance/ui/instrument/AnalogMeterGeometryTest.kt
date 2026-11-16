package dev.anima.resonance.ui.instrument

import dev.anima.resonance.presentation.MainScreenUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure needle/tick geometry for the analog meter. The meter maps the
 * existing normalized cents position (-1..1, 0 = center) onto a symmetric
 * 90° needle sweep around vertical (±45° at ±50¢); travel is clamped at the
 * mechanical stops without touching the underlying pitch calculation.
 *
 * Flagship vintage dial: the needle is ALWAYS rendered. With no reliable
 * reading it rests gently just past the flat-side stop (≈ −52¢, like an
 * unpowered analog meter) rather than disappearing. The cents mapping stays
 * linear and center-zero; negative cents deflect LEFT, positive RIGHT.
 */
class AnalogMeterGeometryTest {

    private val halfSweep = 45.0 // SWEEP_DEGREES / 2 (90° sweep)

    @Test
    fun centerPositionPointsStraightUp() {
        assertEquals(0.0, AnalogMeterGeometry.needleAngle(0.0), 1e-9)
    }

    @Test
    fun fullScalePositionsReachTheMechanicalStops() {
        assertEquals(halfSweep, AnalogMeterGeometry.needleAngle(1.0), 1e-9)
        assertEquals(-halfSweep, AnalogMeterGeometry.needleAngle(-1.0), 1e-9)
    }

    @Test
    fun mappingIsLinearAcrossTheScale() {
        assertEquals(halfSweep * 0.5, AnalogMeterGeometry.needleAngle(0.5), 1e-9)
        assertEquals(-halfSweep * 0.25, AnalogMeterGeometry.needleAngle(-0.25), 1e-9)
    }

    @Test
    fun negativeCentsDeflectLeftPositiveRight() {
        assertTrue(AnalogMeterGeometry.needleAngleForCents(-9) < 0.0, "flat must deflect left")
        assertTrue(AnalogMeterGeometry.needleAngleForCents(9) > 0.0, "sharp must deflect right")
        assertEquals(-9.0 / 50.0 * halfSweep, AnalogMeterGeometry.needleAngleForCents(-9), 1e-9)
    }

    @Test
    fun needleTravelIsClampedBeyondFullScale() {
        assertEquals(halfSweep, AnalogMeterGeometry.needleAngle(2.5), 1e-9)
        assertEquals(-halfSweep, AnalogMeterGeometry.needleAngle(-40.0), 1e-9)
    }

    @Test
    fun centsMapThroughTheSameFullScaleAsTheUiState() {
        assertEquals(-halfSweep, AnalogMeterGeometry.needleAngleForCents(-50), 1e-9)
        assertEquals(0.0, AnalogMeterGeometry.needleAngleForCents(0), 1e-9)
        assertEquals(halfSweep, AnalogMeterGeometry.needleAngleForCents(50), 1e-9)
        assertEquals(halfSweep * 0.5, AnalogMeterGeometry.needleAngleForCents(25), 1e-9)
        // Beyond full scale the needle stays at the stop…
        assertEquals(halfSweep, AnalogMeterGeometry.needleAngleForCents(120), 1e-9)
        assertEquals(-halfSweep, AnalogMeterGeometry.needleAngleForCents(-90), 1e-9)
    }

    @Test
    fun clampingFlagMatchesTheUiStateContract() {
        assertFalse(AnalogMeterGeometry.isClamped(50))
        assertFalse(AnalogMeterGeometry.isClamped(-50))
        assertTrue(AnalogMeterGeometry.isClamped(51))
        assertTrue(AnalogMeterGeometry.isClamped(-80))
        for (cents in -120..120) {
            assertEquals(
                MainScreenUi.isMeterClamped(cents),
                AnalogMeterGeometry.isClamped(cents),
                "clamped flag diverged at $cents cents",
            )
        }
    }

    @Test
    fun geometryAgreesWithTheTestedUiPositionMapping() {
        for (cents in -120..120) {
            assertEquals(
                AnalogMeterGeometry.needleAngle(MainScreenUi.meterPosition(cents)),
                AnalogMeterGeometry.needleAngleForCents(cents),
                1e-9,
                "angle diverged at $cents cents",
            )
        }
    }

    @Test
    fun ticksAreEvenlySpacedAndSymmetric() {
        val step = AnalogMeterGeometry.tickAngle(10) - AnalogMeterGeometry.tickAngle(0)
        assertEquals(halfSweep / 5.0, step, 1e-9) // 10¢ of ±50¢ full scale
        for (cents in -50..40 step 10) {
            assertEquals(
                step,
                AnalogMeterGeometry.tickAngle(cents + 10) - AnalogMeterGeometry.tickAngle(cents),
                1e-9,
            )
        }
        assertEquals(-AnalogMeterGeometry.tickAngle(30), AnalogMeterGeometry.tickAngle(-30), 1e-9)
    }

    @Test
    fun inTuneBandMatchesTheStateMachineExitTolerance() {
        assertEquals(8.0 / 50.0 * halfSweep, AnalogMeterGeometry.inTuneBandHalfAngle(8.0), 1e-9)
        assertEquals(0.0, AnalogMeterGeometry.inTuneBandHalfAngle(0.0), 1e-9)
    }

    @Test
    fun restAngleIsJustPastTheFlatMechanicalStop() {
        // The unpowered rest sits just past the −50¢ stop (≈ −52¢), like a
        // real analog meter at rest — never centered, never a valid reading.
        assertTrue(AnalogMeterGeometry.REST_ANGLE < -halfSweep, "rest must be past the flat stop")
        assertTrue(AnalogMeterGeometry.REST_ANGLE > -halfSweep - 5.0, "rest only slightly past the stop")
        assertEquals(-52.0 / 50.0 * halfSweep, AnalogMeterGeometry.REST_ANGLE, 1e-9)
    }

    @Test
    fun needleIsAlwaysRenderedEvenWithNoSignal() {
        // The needle never disappears; no-signal rests it at the left stop.
        assertTrue(AnalogMeterGeometry.needleShown(active = false))
        assertTrue(AnalogMeterGeometry.needleShown(active = true))
    }
}