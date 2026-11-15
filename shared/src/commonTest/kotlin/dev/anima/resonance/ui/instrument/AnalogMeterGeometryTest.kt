package dev.anima.resonance.ui.instrument

import dev.anima.resonance.presentation.MainScreenUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure needle/tick geometry for the analog meter. The meter maps the
 * existing normalized cents position (-1..1, 0 = center) onto a symmetric
 * needle sweep around vertical; travel is clamped at the mechanical stops
 * without touching the underlying pitch calculation.
 */
class AnalogMeterGeometryTest {

    @Test
    fun centerPositionPointsStraightUp() {
        assertEquals(0.0, AnalogMeterGeometry.needleAngle(0.0), 1e-9)
    }

    @Test
    fun fullScalePositionsReachTheMechanicalStops() {
        assertEquals(45.0, AnalogMeterGeometry.needleAngle(1.0), 1e-9)
        assertEquals(-45.0, AnalogMeterGeometry.needleAngle(-1.0), 1e-9)
    }

    @Test
    fun mappingIsLinearAcrossTheScale() {
        assertEquals(22.5, AnalogMeterGeometry.needleAngle(0.5), 1e-9)
        assertEquals(-11.25, AnalogMeterGeometry.needleAngle(-0.25), 1e-9)
    }

    @Test
    fun needleTravelIsClampedBeyondFullScale() {
        assertEquals(45.0, AnalogMeterGeometry.needleAngle(2.5), 1e-9)
        assertEquals(-45.0, AnalogMeterGeometry.needleAngle(-40.0), 1e-9)
    }

    @Test
    fun centsMapThroughTheSameFullScaleAsTheUiState() {
        assertEquals(-45.0, AnalogMeterGeometry.needleAngleForCents(-50), 1e-9)
        assertEquals(0.0, AnalogMeterGeometry.needleAngleForCents(0), 1e-9)
        assertEquals(45.0, AnalogMeterGeometry.needleAngleForCents(50), 1e-9)
        assertEquals(22.5, AnalogMeterGeometry.needleAngleForCents(25), 1e-9)
        // Beyond full scale the needle stays at the stop…
        assertEquals(45.0, AnalogMeterGeometry.needleAngleForCents(120), 1e-9)
        assertEquals(-45.0, AnalogMeterGeometry.needleAngleForCents(-90), 1e-9)
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
        assertEquals(9.0, step, 1e-9)
        for (cents in -50..40 step 10) {
            assertEquals(
                step,
                AnalogMeterGeometry.tickAngle(cents + 10) - AnalogMeterGeometry.tickAngle(cents),
                1e-9,
            )
        }
        assertEquals(
            -AnalogMeterGeometry.tickAngle(30),
            AnalogMeterGeometry.tickAngle(-30),
            1e-9,
        )
    }

    @Test
    fun inTuneBandMatchesTheStateMachineExitTolerance() {
        // The state machine exits "in tune" beyond ±8 cents; the painted
        // band must cover exactly that region on the same angular scale.
        assertEquals(8.0 / 50.0 * 45.0, AnalogMeterGeometry.inTuneBandHalfAngle(8.0), 1e-9)
        assertEquals(0.0, AnalogMeterGeometry.inTuneBandHalfAngle(0.0), 1e-9)
    }

    @Test
    fun restAngleIsTheFlatMechanicalStop() {
        // The mechanical rest stop is the flat side (a geometric constant)…
        assertEquals(-45.0, AnalogMeterGeometry.REST_ANGLE, 1e-9)
        assertTrue(AnalogMeterGeometry.REST_ANGLE < 0.0)
    }

    @Test
    fun noSignalHidesTheNeedleRatherThanParkingAtFlatStop() {
        // …but with no reliable reading the needle is HIDDEN (hub stays),
        // so the meter never implies a flat pitch. Only a live reading shows it.
        assertFalse(AnalogMeterGeometry.needleShown(active = false))
        assertTrue(AnalogMeterGeometry.needleShown(active = true))
    }
}