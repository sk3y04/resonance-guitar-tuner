package dev.anima.resonance.presentation

import dev.anima.resonance.music.TuningPreset
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure mapping from pipeline snapshot + settings to [MainScreenUiState]
 * Compose-free and deterministic — this is the tested UI behavior
 * contract that the screen renders.
 */
object MainScreenUi {

    const val METER_FULL_SCALE_CENTS: Double = 50.0

    /** Normalized dial position for [cents]: linear, clamped to -1..1. */
    fun meterPosition(cents: Int): Double =
        (cents / METER_FULL_SCALE_CENTS).coerceIn(-1.0, 1.0)

    /** True when |cents| exceeds the meter's full scale (pointer clamped). */
    fun isMeterClamped(cents: Int): Boolean = abs(cents) > METER_FULL_SCALE_CENTS

    fun toUiState(
        snapshot: TunerSnapshot?,
        permission: MicPermissionState,
        mode: TunerMode,
        preset: TuningPreset?,
        selectedStringIndex: Int?,
        concertPitchHz: Double = 440.0,
        captureError: Boolean = false,
        // Sheet inputs (built-ins + customs, favorites, selection)
        presets: List<TuningPreset> = emptyList(),
        favoritePresetIds: Set<String> = emptySet(),
        selectedPresetId: String = "standard",
        // Enharmonic display preference (identity never changes)
        accidentalSpelling: dev.anima.resonance.music.Spelling = dev.anima.resonance.music.Spelling.NATURAL,
    ): MainScreenUiState {
        val status = snapshot?.status ?: TunerStatus.IDLE
        val reading = snapshot?.reading
        val note = reading?.note
        val hasPitch = reading != null && note != null

        val ctaVisible = permission == MicPermissionState.DENIED_PERMANENT
        val hint = when {
            captureError -> "Microphone capture failed — check input and retry."
            status == TunerStatus.CLIPPING ->
                "Signal is clipping — lower the volume or move away."
            status == TunerStatus.STALE ->
                "Signal lost — last reading shown."
            status == TunerStatus.MEASURED -> null
            status == TunerStatus.UNSTABLE -> "Stabilizing…"
            else -> "Play a note near the microphone."
        }

        val cents = if (hasPitch) reading!!.cents else 0
        val directionText = when {
            !hasPitch -> null
            reading!!.inTune -> "In tune"
            reading.direction == PitchDirection.FLAT -> "Tune up"
            else -> "Tune down"
        }

        val inInstrument = mode == TunerMode.INSTRUMENT && preset != null
        // The glyph renders the spelled name (letter + accidental)
        // under the user's preference; octave is read off the same label so
        // folded notes (Cb3 → B2) stay consistent.
        val spelled = note?.spelledLabel(accidentalSpelling)
        return MainScreenUiState(
            status = status,
            permission = permission,
            permissionCtaVisible = ctaVisible,
            permissionCtaText = "App settings",
            meterPosition = if (hasPitch) meterPosition(cents) else 0.0,
            meterClamped = if (hasPitch) isMeterClamped(cents) else false,
            noteLabel = spelled?.dropLast(1),
            octave = spelled?.last()?.toString(),
            frequencyText = if (hasPitch) formatHz(reading!!.frequencyHz) else null,
            centsText = if (hasPitch) formatCents(cents) else null,
            directionText = directionText,
            statusHint = hint,
            inTune = hasPitch && reading!!.inTune,
            captureError = captureError,
            mode = mode,
            presetName = if (inInstrument) preset!!.name else null,
            targetLabels = if (inInstrument) preset!!.strings.map { it.note.spelledLabel(accidentalSpelling) } else emptyList(),
            selectedStringIndex = if (inInstrument) selectedStringIndex else null,
            concertPitchHz = concertPitchHz,
            presetRows = PresetSheetUi.toRows(presets, favoritePresetIds, selectedPresetId, accidentalSpelling),
            concertLabel = PresetSheetUi.concertPitchLabel(concertPitchHz),
            concertError = PresetSheetUi.concertPitchError(concertPitchHz),
            accidentalSpelling = accidentalSpelling,
        )
    }

    /** "82.4 Hz" — one decimal, locale-independent. */
    private fun formatHz(freq: Double): String {
        val tenths = (freq * 10.0).roundToInt()
        return "${tenths / 10}.${tenths % 10} Hz"
    }

    /** "-3" / "+12" / "0" — explicit sign for non-zero values. */
    private fun formatCents(cents: Int): String =
        when {
            cents > 0 -> "+$cents"
            else -> cents.toString()
        }
}
