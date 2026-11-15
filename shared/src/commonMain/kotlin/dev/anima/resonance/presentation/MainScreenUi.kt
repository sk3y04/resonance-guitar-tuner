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
        // True while capture is actually open. Defaults to the common
        // test/demo assumption; the controller always passes the real flag.
        captureActive: Boolean = permission == MicPermissionState.GRANTED,
    ): MainScreenUiState {
        val status = snapshot?.status ?: TunerStatus.IDLE
        val reading = snapshot?.reading

        // A measurement is only "live" when the state machine actually
        // produced a frequency in a state that can carry one. STALE and
        // INSUFFICIENT_SIGNAL deliberately return frequencyHz = 0: exposing
        // that as "0.0 Hz / 0 cents / Tune up" is a false measurement.
        val hasLivePitch = reading != null &&
            reading.note != null &&
            reading.frequencyHz > 0.0 &&
            (status == TunerStatus.MEASURED ||
                status == TunerStatus.UNSTABLE ||
                status == TunerStatus.CLIPPING)
        // Clipping means the waveform is at full scale: keep the note class as
        // context but never present its numeric deviation as trustworthy.
        val reliable = hasLivePitch && status != TunerStatus.CLIPPING
        val liveReading = if (hasLivePitch) reading else null
        val note = liveReading?.note
        val cents = if (reliable) liveReading?.cents ?: 0 else 0

        val granted = permission == MicPermissionState.GRANTED
        val permissionHint = when (permission) {
            MicPermissionState.GRANTED -> null
            MicPermissionState.NOT_REQUESTED, MicPermissionState.DENIED ->
                "Microphone access is needed to tune. Allow it to start listening."
            MicPermissionState.DENIED_PERMANENT ->
                "Microphone access is off. Enable it in Settings to start tuning."
        }
        val ctaVisible = !granted
        val ctaText = if (permission == MicPermissionState.DENIED_PERMANENT) {
            "Open app settings"
        } else {
            "Allow microphone"
        }

        val inInstrument = mode == TunerMode.INSTRUMENT && preset != null
        val hint = when {
            // The permission banner carries its own explanation.
            !granted -> null
            captureError -> "Microphone unavailable — check your input, then retry."
            !captureActive -> "Microphone is off."
            // One short, readable primary message per no-signal state
            // (shown in the readout, never repeated over the meter face).
            status == TunerStatus.CLIPPING -> "Signal clipping — lower the volume."
            status == TunerStatus.STALE -> "Signal lost — play again."
            status == TunerStatus.INSUFFICIENT_SIGNAL -> "Signal too weak — move closer."
            status == TunerStatus.MEASURED ->
                if (inInstrument && abs(cents) > 100) {
                    "Check the target string — this note is far from the highlighted string."
                } else {
                    null
                }
            status == TunerStatus.UNSTABLE -> "Hold the note steady…"
            else -> "Play a note"
        }

        val directionText = when {
            !reliable || liveReading == null -> null
            liveReading.inTune -> "In tune"
            liveReading.direction == PitchDirection.FLAT -> "Tune up"
            else -> "Tune down"
        }

        val micStatusText = when {
            !granted -> "Microphone off"
            captureError -> "Microphone unavailable"
            !captureActive -> "Microphone off"
            else -> "Listening"
        }

        // AUTO mode compares against the nearest preset string but the old
        // screen only highlighted a manual lock, leaving the player unable to
        // see which target was being used. Expose the active target: manual
        // lock wins; otherwise the detected stable note identifies the string.
        val autoStringIndex = if (inInstrument && selectedStringIndex == null && note != null) {
            preset.strings.indexOfFirst { it.note == note }.takeIf { it >= 0 }
        } else {
            null
        }
        val activeStringIndex = when {
            !inInstrument -> null
            selectedStringIndex != null -> selectedStringIndex
            else -> autoStringIndex
        }
        // The glyph renders the spelled name (letter + accidental)
        // under the user's preference; octave is read off the same label so
        // folded notes (Cb3 → B2) stay consistent.
        val spelled = note?.spelledLabel(accidentalSpelling)
        return MainScreenUiState(
            status = status,
            permission = permission,
            captureActive = captureActive,
            micStatusText = micStatusText,
            permissionCtaVisible = ctaVisible,
            permissionCtaText = ctaText,
            permissionCtaOpensSettings = permission == MicPermissionState.DENIED_PERMANENT,
            permissionHint = permissionHint,
            meterPosition = if (reliable) meterPosition(cents) else 0.0,
            meterClamped = if (reliable) isMeterClamped(cents) else false,
            meterActive = reliable,
            noteLabel = spelled?.dropLast(1),
            octave = spelled?.last()?.toString(),
            frequencyText = if (reliable) formatHz(liveReading?.frequencyHz ?: 0.0) else null,
            centsText = if (reliable) formatCents(cents) else null,
            directionText = directionText,
            statusHint = hint,
            inTune = reliable && liveReading?.inTune == true,
            captureError = captureError,
            mode = mode,
            presetName = if (inInstrument) preset.name else null,
            targetLabels = if (inInstrument) {
                preset.strings.map { it.note.spelledLabel(accidentalSpelling) }
            } else {
                emptyList()
            },
            selectedStringIndex = if (inInstrument) selectedStringIndex else null,
            activeStringIndex = activeStringIndex,
            stringLocked = inInstrument && selectedStringIndex != null,
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
