package dev.anima.resonance.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tuning rows show the string sequence with the octave as secondary type
 * ("D2 A2 D3 F♯3 A3 D4"). The row data is a flat preview string, so it is parsed
 * into note/octave tokens once — pure and testable, never string-sliced in the
 * composable.
 */
class NoteSequenceTest {

    @Test
    fun splitsALabelIntoNoteAndOctave() {
        val tokens = NoteSequence.parse("D2 A2 D3 F#3 A3 D4")
        assertEquals(6, tokens.size)
        assertEquals(NoteToken("D", "2"), tokens[0])
        assertEquals(NoteToken("F#", "3"), tokens[3])
        assertEquals(NoteToken("D", "4"), tokens[5])
    }

    @Test
    fun toleratesExtraWhitespaceAndEmptyInput() {
        assertEquals(emptyList(), NoteSequence.parse(""))
        assertEquals(emptyList(), NoteSequence.parse("   "))
        assertEquals(2, NoteSequence.parse("  E2   B3  ").size)
    }

    @Test
    fun aLabelWithoutAnOctaveKeepsItsWholeName() {
        val token = NoteSequence.parse("A").single()
        assertEquals("A", token.label)
        assertNull(token.octave)
    }

    @Test
    fun flatAndSharpNamesKeepTheirAccidental() {
        assertEquals(NoteToken("Bb", "2"), NoteSequence.parse("Bb2").single())
        assertEquals(NoteToken("C#", "4"), NoteSequence.parse("C#4").single())
    }

    @Test
    fun theSequenceIsRenderedWithSpacesBetweenTokens() {
        assertEquals("D2 A2", NoteSequence.display(NoteSequence.parse("D2 A2")))
    }
}