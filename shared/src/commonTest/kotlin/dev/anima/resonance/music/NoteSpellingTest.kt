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
}
