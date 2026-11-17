package dev.anima.resonance.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The configuration overlay is ONE surface with three task destinations.
 * Entry points deep-link into a destination; the destination strip inside the
 * overlay keeps the user oriented. This is pure mapping — host-testable.
 */
class SheetSectionTest {

    @Test
    fun instrumentPrimaryEntryOpensTheTuningDestination() {
        assertEquals(SheetSection.TUNING, SheetFocus.forInstrumentPresetEntry())
    }

    @Test
    fun chromaticHasNoPresetToChooseSoItsEntryOpensCalibration() {
        assertEquals(SheetSection.CALIBRATION, SheetFocus.forInstrumentPresetEntry(mode = TunerMode.CHROMATIC))
    }

    @Test
    fun theSettingsKeyOpensCalibration() {
        assertEquals(SheetSection.CALIBRATION, SheetFocus.forSettings())
    }

    @Test
    fun theCalibrationSummaryOpensCalibration() {
        assertEquals(SheetSection.CALIBRATION, SheetFocus.forCalibration())
    }

    @Test
    fun aDestinationThatRendersFirstNeedsNoScroll() {
        assertTrue(SheetFocus.isDefaultTop(TunerMode.INSTRUMENT, SheetSection.TUNING))
        assertTrue(SheetFocus.isDefaultTop(TunerMode.CHROMATIC, SheetSection.CALIBRATION))
        assertFalse(SheetFocus.isDefaultTop(TunerMode.INSTRUMENT, SheetSection.CALIBRATION))
        assertFalse(SheetFocus.isDefaultTop(TunerMode.INSTRUMENT, SheetSection.MANAGE))
        assertFalse(SheetFocus.isDefaultTop(TunerMode.CHROMATIC, SheetSection.TUNING))
    }

    @Test
    fun everyDestinationIsReachableInBothModes() {
        assertEquals(
            listOf(SheetSection.TUNING, SheetSection.CALIBRATION, SheetSection.MANAGE),
            SheetSection.entries.toList(),
        )
    }
}