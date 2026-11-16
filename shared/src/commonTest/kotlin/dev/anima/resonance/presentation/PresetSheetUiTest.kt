package dev.anima.resonance.presentation

import dev.anima.resonance.music.BuiltInTunings
import dev.anima.resonance.music.Note
import dev.anima.resonance.music.TuningPreset
import dev.anima.resonance.music.TuningString
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun stepConcertPitchStepsByTenths() {
        assertEquals(439.9, PresetSheetUi.stepConcertPitch(440.0, up = false), 1e-9)
        assertEquals(440.0, PresetSheetUi.stepConcertPitch(439.9, up = true), 1e-9)
        assertEquals(440.1, PresetSheetUi.stepConcertPitch(440.0, up = true), 1e-9)
    }

    @Test
    fun stepConcertPitchClampsAtProductBounds() {
        assertEquals(400.0, PresetSheetUi.stepConcertPitch(400.0, up = false), 1e-9)
        assertEquals(480.0, PresetSheetUi.stepConcertPitch(480.0, up = true), 1e-9)
        assertEquals(400.0, PresetSheetUi.stepConcertPitch(400.2, up = false, step = 1.0), 1e-9)
        assertEquals(480.0, PresetSheetUi.stepConcertPitch(479.8, up = true, step = 1.0), 1e-9)
    }

    @Test
    fun coarseConcertStepIsOneHertzAndStaysOnTheTenthGrid() {
        assertEquals(441.0, PresetSheetUi.stepConcertPitch(440.0, up = true, step = 1.0), 1e-9)
        assertEquals(431.0, PresetSheetUi.stepConcertPitch(432.0, up = false, step = 1.0), 1e-9)
    }

    @Test
    fun concertPitchLabelShowsTenthsPrecision() {
        // Spec §2: 0.1 Hz steps — the label must not truncate the tenth.
        assertEquals("A4 = 439.9 Hz", PresetSheetUi.concertPitchLabel(439.9))
        assertEquals("A4 = 440 Hz", PresetSheetUi.concertPitchLabel(440.0))
    }

    @Test
    fun parseConcertPitchAcceptsPeriodAndCommaAndRoundsToTenth() {
        assertEquals(442.5, PresetSheetUi.parseConcertPitch("442.5")!!, 1e-9)
        assertEquals(442.5, PresetSheetUi.parseConcertPitch("442,5")!!, 1e-9)
        assertEquals(440.0, PresetSheetUi.parseConcertPitch(" 440 ")!!, 1e-9)
        assertEquals(442.3, PresetSheetUi.parseConcertPitch("442.34")!!, 1e-9)
    }

    @Test
    fun parseConcertPitchRejectsBlankAndGarbageWithoutCoercing() {
        assertNull(PresetSheetUi.parseConcertPitch(""))
        assertNull(PresetSheetUi.parseConcertPitch("   "))
        assertNull(PresetSheetUi.parseConcertPitch("abc"))
        assertNull(PresetSheetUi.parseConcertPitch("44x0"))
    }
}
