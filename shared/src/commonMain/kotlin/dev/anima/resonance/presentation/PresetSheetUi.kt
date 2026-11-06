package dev.anima.resonance.presentation

import dev.anima.resonance.music.TuningPreset

/** One row of the preset sheet (pure — Compose renders this). */
data class PresetRowUi(
    val id: String,
    val name: String,
    val builtIn: Boolean,
    val favorite: Boolean,
    val selected: Boolean,
    /** Low-to-high note labels, e.g. "E2 A2 D3 G3 B3 E4". */
    val stringsPreview: String,
)

/**
 * Pure mappers for the settings/preset sheet. Keep Compose-free so
 * the behavior is host-testable (no rendered screenshots).
 */
object PresetSheetUi {

    const val CONCERT_MIN_HZ = 400.0
    const val CONCERT_MAX_HZ = 480.0
    /** |cents| below this displays as "0" (no sign). */
    const val CENTS_ZERO_DISPLAY = 0.5

    fun toRows(
        presets: List<TuningPreset>,
        favorites: Set<String>,
        selectedId: String,
    ): List<PresetRowUi> = presets.map { p ->
        PresetRowUi(
            id = p.id,
            name = p.name,
            builtIn = p.builtIn,
            favorite = p.id in favorites,
            selected = p.id == selectedId,
            stringsPreview = p.strings.joinToString(" ") { it.note.label },
        )
    }

    fun concertPitchLabel(hz: Double): String = "A4 = ${hz.toInt()} Hz"

    /** Non-null when [hz] is outside the product range 400..480. */
    fun concertPitchError(hz: Double): String? =
        if (hz in CONCERT_MIN_HZ..CONCERT_MAX_HZ) null
        else "Concert pitch must be 400–480 Hz (got ${hz.toInt()})"

    /** Signed integer cents: +12 / -3 / 0 (0 when |cents| < CENTS_ZERO_DISPLAY). */
    fun centsLabel(cents: Double): String {
        val rounded = kotlin.math.round(kotlin.math.abs(cents)).toInt()
        if (rounded == 0) return "0"
        return if (cents < 0) "-$rounded" else "+$rounded"
    }
}
