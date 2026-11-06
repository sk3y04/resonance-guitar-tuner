package dev.anima.resonance.presentation

import dev.anima.resonance.music.TuningPreset

enum class TunerMode { CHROMATIC, INSTRUMENT }

enum class MicPermissionState { NOT_REQUESTED, GRANTED, DENIED, DENIED_PERMANENT }

/**
 * Immutable, Compose-free state for the main tuner screen.
 * Produced by [MainScreenUi.toUiState] from the pipeline snapshot + settings,
 * so the entire UI behavior matrix is unit-testable without a renderer.
 *
 * Design tokens and rendering live in ui/; this type only carries what the
 * screen must show and which semantic tags must exist.
 */
data class MainScreenUiState(
    val status: TunerStatus,
    val permission: MicPermissionState,
    val permissionCtaVisible: Boolean,
    val permissionCtaText: String,
    val meterPosition: Double,       // -1..1, 0 = center (clamped)
    val meterClamped: Boolean,       // |cents| beyond the meter's full scale
    val noteLabel: String?,          // large glyph, e.g. "E"
    val octave: String?,             // "2"
    val frequencyText: String?,      // "82.4 Hz" or null (never fake zeros)
    val centsText: String?,          // "-3" / "+12" / "0"
    val directionText: String?,      // "Tune up" / "Tune down" / "In tune"
    val statusHint: String?,         // listening / clipping / stale hints
    val inTune: Boolean,
    val captureError: Boolean,
    val mode: TunerMode,
    val presetName: String?,
    val targetLabels: List<String>,  // low-to-high with octaves; empty in chromatic
    val selectedStringIndex: Int?,   // exposed semantically in the UI
    val concertPitchHz: Double,
)
