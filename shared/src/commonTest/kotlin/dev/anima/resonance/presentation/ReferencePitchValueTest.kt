package dev.anima.resonance.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The overlay's reference-pitch readout shows a clear "A4 · 440 Hz"
 * value derived from the actual concert-pitch state (one decimal when not
 * whole), distinct from the legacy "A4 = … Hz" sheet label.
 */
class ReferencePitchValueTest {

    @Test
    fun wholeHertzRenderWithoutDecimal() {
        assertEquals("A4 · 440 Hz", PresetSheetUi.referencePitchValue(440.0))
        assertEquals("A4 · 432 Hz", PresetSheetUi.referencePitchValue(432.0))
    }

    @Test
    fun fractionalHertzKeepOneDecimal() {
        assertEquals("A4 · 440.5 Hz", PresetSheetUi.referencePitchValue(440.5))
        assertEquals("A4 · 415.3 Hz", PresetSheetUi.referencePitchValue(415.3))
    }

    @Test
    fun roundingIsToTenths() {
        assertEquals("A4 · 440.1 Hz", PresetSheetUi.referencePitchValue(440.06))
        assertEquals("A4 · 440 Hz", PresetSheetUi.referencePitchValue(439.96))
    }
}