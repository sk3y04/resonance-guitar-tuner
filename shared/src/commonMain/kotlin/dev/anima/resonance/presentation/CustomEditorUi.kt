package dev.anima.resonance.presentation

import dev.anima.resonance.music.Note

/**
 * Pure mappers for the custom-tuning editor form (create/edit).
 * The editor UI renders [CustomEditorUiState]; the controller converts a
 * saved form via [toNotes] into the engine's CRUD API.
 */
object CustomEditorUi {

    const val MAX_STRINGS = 12
    const val MIN_OCTAVE = 1
    const val MAX_OCTAVE = 6

    val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * The same pitch classes spelled with flats. The editor preserves the
     * accidental family of an existing note (an Eb stays Eb while cycling)
     * instead of silently rewriting imported flat tunings as sharps.
     */
    val FLAT_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /** Cycles the note name forward/backward within the chromatic set. */
    fun nextNoteName(current: String, up: Boolean): String {
        val palette = if (current.endsWith("b")) FLAT_NAMES else NOTE_NAMES
        val i = palette.indexOf(current)
        if (i < 0) {
            // Defensive: an unknown name starts from its letter's natural.
            val fallback = NOTE_NAMES.indexOfFirst { it.first() == current.firstOrNull() }
            return NOTE_NAMES[if (fallback < 0) 0 else fallback]
        }
        val next = if (up) (i + 1) % palette.size else (i - 1 + palette.size) % palette.size
        return palette[next]
    }

    /**
     * Converts a domain [Note] into the editor's row while preserving its
     * accidental spelling and octave. This is the structured path used when
     * editing a preset; parsing the human-readable "strings preview" was
     * lossy for flat tunings.
     */
    fun toEditorString(note: Note): EditorStringUi {
        val suffix = when (note.spelling) {
            dev.anima.resonance.music.Spelling.SHARP -> "#"
            dev.anima.resonance.music.Spelling.FLAT -> "b"
            dev.anima.resonance.music.Spelling.NATURAL -> ""
        }
        return EditorStringUi(note.letter + suffix, note.octave)
    }

    /** Cycles the octave within the product range (guitar strings live 1..4). */
    fun nextOctave(current: Int, up: Boolean): Int {
        val next = if (up) current + 1 else current - 1
        return next.coerceIn(MIN_OCTAVE, MAX_OCTAVE)
    }

    fun label(noteName: String, octave: Int): String = "$noteName$octave"

    /** Parses editor labels into notes (delegated to the domain parser). */
    fun toNotes(labels: List<String>): List<Note> = labels.map(Note::fromLabel)

    /**
     * Form validation mirroring the PresetStore contract so the UI
     * can block saves with visible errors instead of engine exceptions.
     * Returns an empty list when the form is valid.
     */
    fun validate(name: String, labels: List<String>): List<String> = buildList {
        if (name.isBlank()) add("Name is required")
        if (labels.isEmpty()) add("At least one string is required")
        if (labels.size > MAX_STRINGS) add("Maximum of $MAX_STRINGS strings")
        labels.forEachIndexed { i, l ->
            val parsed = try { Note.fromLabel(l) } catch (e: IllegalArgumentException) { null }
            if (parsed == null) add("String ${i + 1} is not a valid note")
        }
    }
}

/** One editor row: note name + octave (pure values, no Compose types). */
data class EditorStringUi(val noteName: String, val octave: Int) {
    val label: String get() = CustomEditorUi.label(noteName, octave)
}

/**
 * Editor form state. Lives in MainScreen (remember), rendered by the
 * editor sheet composable — headless tests drive it through callbacks.
 */
data class CustomEditorUiState(
    val name: String = "",
    val strings: List<EditorStringUi> = listOf(EditorStringUi("E", 2)),
    /** Non-null when editing an existing custom preset (its stable ID). */
    val editingId: String? = null,
    val isEdit: Boolean = false,
    val errors: List<String> = emptyList(),
)
