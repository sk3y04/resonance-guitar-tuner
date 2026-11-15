package dev.anima.resonance.presentation

import dev.anima.resonance.music.Note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomEditorUiTest {

    @Test
    fun noteNameCyclesBothDirections() {
        assertEquals("C#", CustomEditorUi.nextNoteName("C", up = true))
        assertEquals("C", CustomEditorUi.nextNoteName("C#", up = false))
        assertEquals("C", CustomEditorUi.nextNoteName("B", up = true), "wraps upward")
        assertEquals("B", CustomEditorUi.nextNoteName("C", up = false), "wraps downward")
    }

    @Test
    fun octaveCyclesWithinProductRange() {
        assertEquals(2, CustomEditorUi.nextOctave(1, up = true))
        assertEquals(1, CustomEditorUi.nextOctave(1, up = false), "clamps at the floor")
        assertEquals(6, CustomEditorUi.nextOctave(6, up = true), "clamps at the ceiling")
        assertEquals(5, CustomEditorUi.nextOctave(6, up = false))
        assertEquals(6, CustomEditorUi.nextOctave(5, up = true))
    }

    @Test
    fun validateRejectsBlankNameEmptyStringsAndOverflow() {
        val blankName = CustomEditorUi.validate("  ", listOf("E2"))
        assertTrue(blankName.any { it.contains("Name") }, "blank name: $blankName")

        val noStrings = CustomEditorUi.validate("X", emptyList())
        assertTrue(noStrings.any { it.contains("string", ignoreCase = true) }, "no strings: $noStrings")

        val tooMany = CustomEditorUi.validate("X", (0 until 13).map { "E2" })
        assertTrue(tooMany.any { it.contains("12") }, "13 strings: $tooMany")

        assertTrue(CustomEditorUi.validate("Open D", listOf("D2", "A2", "D3", "F#3", "A3", "D4")).isEmpty())
    }

    @Test
    fun toNotesParsesLabels() {
        val notes = CustomEditorUi.toNotes(listOf("D2", "F#3"))
        assertEquals(listOf(Note.fromLabel("D2"), Note.fromLabel("F#3")), notes)
    }

    @Test
    fun flatSpellingsArePreservedWhileCycling() {
        // A flat tuning must not be silently rewritten as sharps on edit.
        assertEquals("Eb", CustomEditorUi.toEditorString(Note.fromLabel("Eb2")).noteName)
        assertEquals(2, CustomEditorUi.toEditorString(Note.fromLabel("Eb2")).octave)
        assertEquals("E", CustomEditorUi.nextNoteName("Eb", up = true))
        assertEquals("D", CustomEditorUi.nextNoteName("Eb", up = false))
        assertEquals("B", CustomEditorUi.nextNoteName("Bb", up = true), "flat palette wraps")
        assertEquals("A", CustomEditorUi.nextNoteName("Bb", up = false))
        assertEquals(
            Note.fromLabel("Eb2"),
            CustomEditorUi.toNotes(listOf(CustomEditorUi.toEditorString(Note.fromLabel("Eb2")).label)).single(),
        )
    }

    @Test
    fun sharpAndNaturalSpellingsStillUseTheSharpPalette() {
        assertEquals("C#", CustomEditorUi.nextNoteName("C", up = true))
        assertEquals("B", CustomEditorUi.nextNoteName("A#", up = true))
        assertEquals("D#", CustomEditorUi.toEditorString(Note.fromLabel("D#3")).noteName)
        assertEquals("D", CustomEditorUi.toEditorString(Note.fromLabel("D3")).noteName)
    }
}
