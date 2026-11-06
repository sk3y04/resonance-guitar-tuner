package dev.anima.resonance.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NoteSpellingTest {

    @Test
    fun sharpAndFlatEnharmonicsAgree() {
        assertEquals(
            Note("A#", 4).frequency(440.0),
            Note("Bb", 4).frequency(440.0),
            1e-12,
        )
    }

    @Test
    fun twoArgConstructorParsesSpelling() {
        assertEquals(Note("C#", 4), Note("C", Spelling.SHARP, 4))
        assertEquals(Note("Db", 3), Note("D", Spelling.FLAT, 3))
        assertEquals(Note("A", 4), Note("A", Spelling.NATURAL, 4))
    }

    @Test
    fun enharmonicWrapAround() {
        assertEquals(Note("E#", 4).frequency(440.0), Note("F", 4).frequency(440.0), 1e-12)
        assertEquals(Note("Fb", 4).frequency(440.0), Note("E", 4).frequency(440.0), 1e-12)
    }

    @Test
    fun pitchClassIsSemitoneIndexModulo12() {
        assertEquals(10, Note("Bb", 4).pitchClass)
        assertEquals(5, Note("E#", 4).pitchClass)
        assertEquals(0, Note("C", 2).pitchClass)
        assertEquals(9, Note("A", 4).pitchClass)
    }

    @Test
    fun invalidSpellingOrLetterIsRejected() {
        assertFailsWith<IllegalArgumentException> { Note("Cx", 4) }
        assertFailsWith<IllegalArgumentException> { Note("Q", 4) }
        assertFailsWith<IllegalArgumentException> { Note("X#", 4) }
    }

    // Enharmonic display preference. The stored note
    // identity never changes; only its rendered label does.

    @Test
    fun sharpPreferenceRendersSharpCanonicalForms() {
        assertEquals("A#4", Note.fromLabel("A#4").spelledLabel(Spelling.SHARP))
        assertEquals("A#4", Note.fromLabel("Bb4").spelledLabel(Spelling.SHARP))
        assertEquals("D#3", Note.fromLabel("Eb3").spelledLabel(Spelling.SHARP))
        assertEquals("C3", Note.fromLabel("C3").spelledLabel(Spelling.SHARP))
    }

    @Test
    fun flatPreferenceRendersFlatCanonicalForms() {
        assertEquals("Bb4", Note.fromLabel("A#4").spelledLabel(Spelling.FLAT))
        assertEquals("Db2", Note.fromLabel("C#2").spelledLabel(Spelling.FLAT))
        assertEquals("F5", Note.fromLabel("E#5").spelledLabel(Spelling.FLAT), "E# folds to F")
        assertEquals("B2", Note.fromLabel("Cb3").spelledLabel(Spelling.FLAT), "Cb folds to the lower B")
        assertEquals("C2", Note.fromLabel("C2").spelledLabel(Spelling.FLAT))
    }
}
