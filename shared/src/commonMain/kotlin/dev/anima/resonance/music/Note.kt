package dev.anima.resonance.music

/**
 * A named pitch class with an octave, using scientific pitch notation
 * (A4 = concert pitch). Accidental spelling is part of the note's identity
 * (spelling is handled by a later slice; [name] currently holds a plain
 * letter name such as "A" or "E").
 *
 * Frequencies are derived from note identity + concert pitch — never from
 * rounded frequency literals.
 *
 * TDD slice: [frequency] is a minimal stub (returns 0.0) so the test
 * compiles; the RED state is demonstrated before the real implementation.
 */
data class Note(
    val name: String,
    val octave: Int,
) {
    /** Frequency in Hz derived from this note and the A4 [concertPitchHz]. */
    fun frequency(concertPitchHz: Double): Double = 0.0
}
