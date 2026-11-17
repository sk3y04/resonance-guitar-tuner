package dev.anima.resonance.presentation

/** One note in a tuning's sequence: the pitch name and its octave (if any). */
data class NoteToken(val label: String, val octave: String?)

/**
 * Tuning rows show the string sequence with the octave as secondary type.
 * The row data carries a flat preview string ("D2 A2 D3 F#3 A3 D4"), so it is
 * parsed here once — pure, host-testable, and free of string slicing inside
 * composables.
 */
object NoteSequence {

    fun parse(preview: String): List<NoteToken> =
        preview.split(' ')
            .filter { it.isNotBlank() }
            .map { token ->
                val last = token.last()
                if (last.isDigit()) {
                    NoteToken(token.dropLast(1), last.toString())
                } else {
                    NoteToken(token, null)
                }
            }

    /** Plain fallback rendering (used for spoken descriptions and tests). */
    fun display(tokens: List<NoteToken>): String =
        tokens.joinToString(" ") { it.label + (it.octave ?: "") }
}