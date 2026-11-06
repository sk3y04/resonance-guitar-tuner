package dev.anima.resonance.music

import kotlin.math.pow

/**
 * Letter-name to semitone offset within a diatonic scale (C = 0).
 * Accidental spelling (C#/Db etc.) arrives in a later slice; until then
 * only natural notes are accepted and any other name is rejected up front.
 */
private val SEMITONES: Map<String, Int> = mapOf(
    "C" to 0,
    "D" to 2,
    "E" to 4,
    "F" to 5,
    "G" to 7,
    "A" to 9,
    "B" to 11,
)

/**
 * A named pitch class with an octave, using scientific pitch notation
 * (A4 = concert pitch). Note identity is (letter name, octave); frequencies
 * are always derived from identity + concert pitch, never from literals.
 */
data class Note(
    val name: String,
    val octave: Int,
) {
    init {
        require(name in SEMITONES) { "Unknown note letter: '$name' (accidental spelling is a later slice)" }
    }

    /**
     * Frequency in Hz derived from this note and the A4 [concertPitchHz].
     * Throws [IllegalArgumentException] for concert pitch outside 400..480 Hz
     * (product contract) — including for persisted/imported values.
     */
    fun frequency(concertPitchHz: Double): Double {
        require(concertPitchHz in 400.0..480.0) { "Concert pitch must be in 400..480 Hz, got $concertPitchHz" }
        val midi = 12 * (octave + 1) + SEMITONES.getValue(name)
        return concertPitchHz * 2.0.pow((midi - 69) / 12.0)
    }
}
