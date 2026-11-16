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
    /** True while the engine really has the capture device open. */
    val captureActive: Boolean,
    /** Short visible/announced capture state: "Listening", "Microphone off", … */
    val micStatusText: String,
    val permissionCtaVisible: Boolean,
    val permissionCtaText: String,
    /** True → the CTA must open app settings; false → request permission again. */
    val permissionCtaOpensSettings: Boolean,
    /** Explanatory copy shown in the permission banner (null when granted). */
    val permissionHint: String?,
    val meterPosition: Double,       // -1..1, 0 = center (clamped)
    val meterClamped: Boolean,       // |cents| beyond the meter's full scale
    /** False when there is no trustworthy live measurement (silence/stale/clipping). */
    val meterActive: Boolean,
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
    val selectedStringIndex: Int?,   // manual lock only; null = AUTO
    /** The string being compared: manual lock, or the AUTO-detected target. */
    val activeStringIndex: Int?,
    /** True when [activeStringIndex] is a manual lock (vs AUTO detection). */
    val stringLocked: Boolean,
    val concertPitchHz: Double,
    // Settings/preset sheet
    val presetRows: List<PresetRowUi> = emptyList(),
    val concertLabel: String = "",
    val concertError: String? = null,
    // Active enharmonic display preference (drives the glyph, targets, rows)
    val accidentalSpelling: dev.anima.resonance.music.Spelling = dev.anima.resonance.music.Spelling.NATURAL,
    // Last import/export result message (null = nothing to show)
    val importResultText: String? = null,
    // Explicit signal presentation phase + readout opacity (1 = live;
    // a brief interruption holds the last reading faded, never as live).
    val signalPhase: SignalPhase = SignalPhase.WAITING,
    val readingAlpha: Float = 1f,
    // The selected instrument preset's string count, available in BOTH
    // modes so the meter/readout geometry and the config-entry position stay
    // stable when switching modes (chromatic reserves the rail as negative space).
    val railStringCount: Int = 0,
)
