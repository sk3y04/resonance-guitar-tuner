package dev.anima.resonance.presentation

import dev.anima.resonance.music.TuningPreset
import kotlin.math.roundToLong

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
        spelling: dev.anima.resonance.music.Spelling = dev.anima.resonance.music.Spelling.NATURAL,
    ): List<PresetRowUi> = presets.map { p ->
        PresetRowUi(
            id = p.id,
            name = p.name,
            builtIn = p.builtIn,
            favorite = p.id in favorites,
            selected = p.id == selectedId,
            stringsPreview = p.strings.joinToString(" ") { it.note.spelledLabel(spelling) },
        )
    }

    /**
     * A4 reference label. Spec §2: the product steps by 0.1 Hz, so tenths
     * must be visible (439.9); whole numbers stay clean ("A4 = 440 Hz").
     */
    fun concertPitchLabel(hz: Double): String {
        val tenths = (hz * 10).roundToLong()
        return if (tenths % 10 == 0L) "A4 = ${tenths / 10} Hz"
        else "A4 = ${tenths / 10}.${tenths % 10} Hz"
    }

    /**
     * Steps the A4 reference by ±0.1 Hz (spec §2), rounded to the tenth and
     * clamped to the product range 400..480 Hz.
     */
    fun stepConcertPitch(hz: Double, up: Boolean, step: Double = 0.1): Double {
        val tenths = (hz * 10).roundToLong() + if (up) 1L else -1L
        return (tenths.coerceIn((CONCERT_MIN_HZ * 10).toLong(), (CONCERT_MAX_HZ * 10).toLong())) / 10.0
    }

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
