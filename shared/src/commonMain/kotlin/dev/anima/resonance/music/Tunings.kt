package dev.anima.resonance.music

/**
 * One string's target in a tuning. [id] is stable and identifies the string
 * (never list position); [note] carries the exact spelling + octave.
 */
data class TuningString(
    val id: String,
    val note: Note,
)

/**
 * A named tuning. [strings] is ordered LOW to HIGH (index 0 = lowest pitch,
 * the guitar's 6th string). [id] is stable across app versions and imports.
 */
data class TuningPreset(
    val id: String,
    val name: String,
    val strings: List<TuningString>,
    val builtIn: Boolean = false,
) {
    init {
        require(strings.isNotEmpty()) { "Preset '$id' must have at least one string" }
        require(strings.map { it.id }.toSet().size == strings.size) { "Duplicate string ids in preset '$id'" }
    }

    /** Frequency of the [index]-th string (low-to-high) for a concert pitch. */
    fun frequencyAt(index: Int, concertPitchHz: Double): Double =
        strings[index].note.frequency(concertPitchHz)
}

/**
 * Built-in tuning presets (guitar, six strings, low-to-high).
 * Spellings are the conventional ones for each tuning (e.g. F#3 for Open D,
 * flats for Half-Step Down).
 */
object BuiltInTunings {

    private fun preset(id: String, name: String, vararg notes: Pair<String, Int>): TuningPreset =
        TuningPreset(
            id = id,
            name = name,
            builtIn = true,
            strings = notes.mapIndexed { i, (n, octave) ->
                TuningString(id = "$id-s${i + 1}", note = Note(n, octave))
            },
        )

    /** E2 A2 D3 G3 B3 E4 */
    val standard = preset("standard", "Standard", "E" to 2, "A" to 2, "D" to 3, "G" to 3, "B" to 3, "E" to 4)

    /** D2 A2 D3 G3 B3 E4 */
    val dropD = preset("drop-d", "Drop D", "D" to 2, "A" to 2, "D" to 3, "G" to 3, "B" to 3, "E" to 4)

    /** D2 A2 D3 G3 A3 D4 */
    val dadgad = preset("dadgad", "DADGAD", "D" to 2, "A" to 2, "D" to 3, "G" to 3, "A" to 3, "D" to 4)

    /** D2 A2 D3 F#3 A3 D4 */
    val openD = preset("open-d", "Open D", "D" to 2, "A" to 2, "D" to 3, "F#" to 3, "A" to 3, "D" to 4)

    /** D2 G2 D3 G3 B3 D4 */
    val openG = preset("open-g", "Open G", "D" to 2, "G" to 2, "D" to 3, "G" to 3, "B" to 3, "D" to 4)

    /** Eb2 Ab2 Db3 Gb3 Bb3 Eb4 */
    val halfStepDown = preset(
        "half-step-down", "Half-Step Down",
        "Eb" to 2, "Ab" to 2, "Db" to 3, "Gb" to 3, "Bb" to 3, "Eb" to 4,
    )

    val all: List<TuningPreset> = listOf(standard, dropD, dadgad, openD, openG, halfStepDown)

    /** Look up by stable id. Returns null for unknown/custom ids. */
    fun byId(id: String): TuningPreset? = all.firstOrNull { it.id == id }
}
