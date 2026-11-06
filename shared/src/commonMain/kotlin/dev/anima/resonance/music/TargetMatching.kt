package dev.anima.resonance.music

import kotlin.math.abs
import kotlin.math.pow

/**
 * Result of matching a measured frequency against target notes.
 * [cents] is the SIGNED deviation from [targetHz], unclamped — the display may
 * clamp for drawing, but the value preserves the full deviation (contract).
 */
data class TargetMatch(
    val note: Note,
    val targetHz: Double,
    val cents: Int,
)

/**
 * Nearest-target selection over a caller-supplied target set.
 *
 * "Nearest" = smallest absolute signed cents. Exact ties break toward the
 * LOWER-frequency target (deterministic). Manual string locking is expressed
 * by the caller passing a single-element target list; the matcher then keeps
 * the full signed cents even far outside any meter range.
 */
object TargetMatcher {

    fun nearest(measuredHz: Double, targets: List<Note>, concertPitchHz: Double): TargetMatch {
        require(measuredHz > 0.0) { "measuredHz must be > 0, got $measuredHz" }
        require(targets.isNotEmpty()) { "targets must not be empty" }
        val best = targets.minWithOrNull(
            compareBy(
                { abs(Cents.deviation(measuredHz, it.frequency(concertPitchHz))) },
                { it.frequency(concertPitchHz) },
            ),
        )!!
        val targetHz = best.frequency(concertPitchHz)
        return TargetMatch(best, targetHz, Cents.deviation(measuredHz, targetHz))
    }
}

/**
 * Chromatic target set for a concert pitch: every note (canonical
 * sharp-prefixed spelling) whose frequency lies within [lowHz]..[highHz]
 * (default: the 55..1200 Hz design band), ascending.
 */
object ChromaticTargets {

    private val LETTERS = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** MIDI scan window: C1..C8 comfortably covers any 400..480 Hz band. */
    private const val MIN_MIDI = 12
    private const val MAX_MIDI = 108

    fun inRange(concertPitchHz: Double, lowHz: Double = 55.0, highHz: Double = 1200.0): List<Note> {
        require(concertPitchHz in 400.0..480.0) { "Concert pitch must be in 400..480 Hz, got $concertPitchHz" }
        require(lowHz > 0.0) { "lowHz must be > 0, got $lowHz" }
        require(highHz > lowHz) { "highHz ($highHz) must exceed lowHz ($lowHz)" }
        val out = ArrayList<Note>(64)
        for (midi in MIN_MIDI..MAX_MIDI) {
            val f = concertPitchHz * 2.0.pow((midi - 69) / 12.0)
            if (f in lowHz..highHz) {
                out += Note(LETTERS[midi % 12], midi / 12 - 1)
            }
        }
        return out
    }
}
