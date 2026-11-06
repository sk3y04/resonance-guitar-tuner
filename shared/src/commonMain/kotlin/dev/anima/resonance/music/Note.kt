package dev.anima.resonance.music

import kotlin.math.pow

/** Accidental spelling of a note name. */
enum class Spelling { NATURAL, SHARP, FLAT }

/**
 * Letter-name to semitone offset within a diatonic scale (C = 0).
 */
private val LETTER_SEMITONES: Map<String, Int> = mapOf(
    "C" to 0,
    "D" to 2,
    "E" to 4,
    "F" to 5,
    "G" to 7,
    "A" to 9,
    "B" to 11,
)

private val SPELLING_OFFSET: Map<Spelling, Int> = mapOf(
    Spelling.NATURAL to 0,
    Spelling.SHARP to 1,
    Spelling.FLAT to -1,
)

/**
 * A named pitch class with an octave, using scientific pitch notation
 * (A4 = concert pitch). Note identity is (letter, accidental spelling, octave);
 * frequencies are always derived from identity + concert pitch, never literals.
 *
 * Enharmonic spellings ("A#"/"Bb", "E#"/"F") produce identical frequencies;
 * [pitchClass] folds them to a single 0..11 index.
 */
data class Note(
    val letter: String,
    val spelling: Spelling = Spelling.NATURAL,
    val octave: Int,
) {
    /** Parses "A", "A#", "Bb". */
    constructor(name: String, octave: Int) : this(
        letter = if (name.length == 1) name else name.dropLast(1),
        spelling = when (name.length) {
            1 -> Spelling.NATURAL
            else -> when (name.last()) {
                '#' -> Spelling.SHARP
                'b' -> Spelling.FLAT
                else -> throw IllegalArgumentException("Unknown spelling in note name: '$name'")
            }
        },
        octave = octave,
    )

    init {
        require(letter in LETTER_SEMITONES) { "Unknown note letter: '$letter'" }
    }

    /** Absolute semitone offset of letter + spelling (may be -1..12, e.g. Cb/E#). */
    private val rawSemitones: Int
        get() = LETTER_SEMITONES.getValue(letter) + SPELLING_OFFSET.getValue(spelling)

    /** Semitone index 0..11 of this note's pitch class (enharmonics folded). */
    val pitchClass: Int
        get() = ((rawSemitones % 12) + 12) % 12

    /**
     * Frequency in Hz derived from this note and the A4 [concertPitchHz].
     * Throws [IllegalArgumentException] for concert pitch outside 400..480 Hz
     * (product contract) — including for persisted/imported values.
     */
    fun frequency(concertPitchHz: Double): Double {
        require(concertPitchHz in 400.0..480.0) { "Concert pitch must be in 400..480 Hz, got $concertPitchHz" }
        val midi = 12 * (octave + 1) + rawSemitones
        return concertPitchHz * 2.0.pow((midi - 69) / 12.0)
    }

    /** Display label with unambiguous octave, e.g. "Bb2", "E4". */
    val label: String
        get() = buildString {
            append(letter)
            when (spelling) {
                Spelling.SHARP -> append('#')
                Spelling.FLAT -> append('b')
                Spelling.NATURAL -> {}
            }
            append(octave)
        }
}
