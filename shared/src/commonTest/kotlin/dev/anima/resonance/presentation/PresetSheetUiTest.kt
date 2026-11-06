package dev.anima.resonance.presentation

import dev.anima.resonance.music.BuiltInTunings
import dev.anima.resonance.music.Note
import dev.anima.resonance.music.TuningPreset
import dev.anima.resonance.music.TuningString
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresetSheetUiTest {

    private fun custom(id: String, name: String, vararg labels: String): TuningPreset =
        TuningPreset(
            id = id,
            name = name,
            builtIn = false,
            strings = labels.mapIndexed { i, l -> TuningString("$id-s${i + 1}", Note.fromLabel(l)) },
        )

    @Test
    fun rowsCarrySelectionFavoriteAndBuiltInFlags() {
        val openD = custom("c1", "Open D", "D2", "A2", "D3", "F#3", "A3", "D4")
        val rows = PresetSheetUi.toRows(
            presets = BuiltInTunings.all + openD,
            favorites = setOf("standard", "c1"),
            selectedId = "c1",
        )
        assertEquals(BuiltInTunings.all.size + 1, rows.size)
        val standard = rows.first { it.id == "standard" }
        assertTrue(standard.favorite)
        assertTrue(standard.builtIn)
        assertFalse(standard.selected)
        val openDRow = rows.first { it.id == "c1" }
        assertTrue(openDRow.selected && openDRow.favorite && !openDRow.builtIn)
    }

    @Test
    fun stringsPreviewIsLowToHigh() {
        val openD = custom("c1", "Open D", "D2", "A2", "D3", "F#3", "A3", "D4")
        val row = PresetSheetUi.toRows(listOf(openD), emptySet(), "c1").single()
        assertEquals("D2 A2 D3 F#3 A3 D4", row.stringsPreview)
        val standard = BuiltInTunings.standard
        val row2 = PresetSheetUi.toRows(listOf(standard), emptySet(), "standard").single()
        assertEquals("E2 A2 D3 G3 B3 E4", row2.stringsPreview)
    }

    @Test
    fun unknownSelectionSelectsNothing() {
        val rows = PresetSheetUi.toRows(BuiltInTunings.all, emptySet(), "nope")
        assertTrue(rows.none { it.selected })
    }

    @Test
    fun concertPitchLabelFormatsHz() {
        assertEquals("A4 = 440 Hz", PresetSheetUi.concertPitchLabel(440.0))
        assertEquals("A4 = 442 Hz", PresetSheetUi.concertPitchLabel(442.0))
    }

    @Test
    fun concertPitchValidation() {
        assertTrue(PresetSheetUi.concertPitchError(440.0) == null)
        assertTrue(PresetSheetUi.concertPitchError(400.0) == null)
        assertTrue(PresetSheetUi.concertPitchError(480.0) == null)
        assertTrue(PresetSheetUi.concertPitchError(399.0) != null)
        assertTrue(PresetSheetUi.concertPitchError(481.0) != null)
    }

    @Test
    fun centsLabelSigns() {
        assertEquals("+12", PresetSheetUi.centsLabel(12.4))
        assertEquals("-3", PresetSheetUi.centsLabel(-3.2))
        assertEquals("0", PresetSheetUi.centsLabel(0.4))
    }
}
