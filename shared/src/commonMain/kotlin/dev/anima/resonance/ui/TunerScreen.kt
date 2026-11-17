package dev.anima.resonance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.music.Spelling
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.SignalPhase
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.ui.instrument.AnalogTuningMeter
import dev.anima.resonance.ui.instrument.ChromaticConfigRow
import dev.anima.resonance.ui.instrument.ConfigSummaryRow
import dev.anima.resonance.ui.instrument.ControlDeck
import dev.anima.resonance.ui.instrument.ControlDeckLayout
import dev.anima.resonance.ui.instrument.FadeInOnChange
import dev.anima.resonance.ui.instrument.InstrumentKey
import dev.anima.resonance.ui.instrument.InstrumentTheme
import dev.anima.resonance.ui.instrument.InstrumentTokens
import dev.anima.resonance.ui.instrument.InstrumentType
import dev.anima.resonance.ui.instrument.JewelLamp
import dev.anima.resonance.ui.instrument.MechanicalModeSelector
import dev.anima.resonance.ui.instrument.MeterAssembly
import dev.anima.resonance.ui.instrument.MeterAssemblyLayout
import dev.anima.resonance.ui.instrument.NoteReadout
import dev.anima.resonance.ui.instrument.RecessedPanel
import dev.anima.resonance.ui.instrument.StringTargets
import dev.anima.resonance.ui.instrument.ToggleRow
import dev.anima.resonance.ui.instrument.TuningSummaryRow
import dev.anima.resonance.ui.instrument.animatedLampColor
import kotlin.math.roundToInt

/**
 * The instrument fills the whole window.
 *
 * No wooden wallpaper, no outer frame, no floating card: the app background is
 * the espresso enclosure colour, the ivory meter + readout band is full-bleed,
 * and the espresso control deck runs edge to edge underneath it and all the way
 * to the bottom of the screen. Only device insets are respected, and only as
 * content padding.
 *
 * Reading order is unchanged: status → meter → readout → mode → tuning →
 * auto-select → string targets → reference row. The control rows stay inside
 * one deck, on one gutter, and the deck's reserved height keeps the meter and
 * the mode row geometrically stable across modes.
 */
@Composable
fun TunerScreen(
    state: MainScreenUiState,
    onModeSelect: (TunerMode) -> Unit,
    onStringSelect: (Int) -> Unit,
    onPermissionCta: () -> Unit,
    onRetryCapture: () -> Unit,
    onRequestPermission: () -> Unit = {},
    onOpenSheet: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onAutoSelectionChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    val granted = state.permission == MicPermissionState.GRANTED
    val fontScale = LocalDensity.current.fontScale
    val largeText = fontScale > 1.3f
    val noteSize = (96f / fontScale).coerceAtMost(64f).sp
    val centsSize = (56f / fontScale).coerceAtMost(34f).sp
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(p.background),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val innerW = maxWidth.value
            val innerH = maxHeight.value
            val topInset = insets.calculateTopPadding().value
            val bottomInset = insets.calculateBottomPadding().value
            val strings = maxOf(state.targetLabels.size, state.railStringCount)
                .takeIf { it > 0 } ?: ControlDeckLayout.DEFAULT_RESERVED_STRINGS

            // Reserve the instrument row stack in BOTH modes so the meter and
            // the mode row never move when the mode changes.
            val fullBlockH = ControlDeckLayout.heightDp(strings, innerW, compact = false)
            var compactBlock = false
            var blockH = fullBlockH
            var assemblyAvailH = innerH - STATUS_BAND_DP - topInset - GAP_TO_BLOCK_DP - blockH
            if (assemblyAvailH < MIN_ASSEMBLY_FULL_DP) {
                compactBlock = true
                blockH = ControlDeckLayout.heightDp(strings, innerW, compact = true)
                assemblyAvailH = innerH - STATUS_BAND_DP - topInset - GAP_TO_BLOCK_DP - blockH
            }
            val fallback = largeText || assemblyAvailH < MIN_FIXED_ASSEMBLY_DP

            Column(Modifier.fillMaxSize()) {
                // ── Acquisition status, on the enclosure colour ──────────────
                Spacer(Modifier.height(topInset.dp))
                StatusStrip(
                    phase = state.signalPhase,
                    statusText = state.micStatusText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(STATUS_BAND_DP.dp)
                        .padding(horizontal = InstrumentTokens.gutter),
                )

                if (fallback) {
                    FallbackLayout(
                        state = state,
                        granted = granted,
                        noteSize = noteSize,
                        centsSize = centsSize,
                        largeText = largeText,
                        compactBlock = true,
                        bottomInset = bottomInset,
                        onModeSelect = onModeSelect,
                        onStringSelect = onStringSelect,
                        onPermissionCta = onPermissionCta,
                        onRetryCapture = onRetryCapture,
                        onRequestPermission = onRequestPermission,
                        onOpenSheet = onOpenSheet,
                        onOpenSettings = onOpenSettings,
                        onAutoSelectionChange = onAutoSelectionChange,
                    )
                } else {
                    // ── Full-bleed measurement band (ivory face + readout) ───
                    if (!granted || state.captureError) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = InstrumentTokens.gutter)) {
                            CapturePanel(
                                state = state,
                                onPermissionCta = onPermissionCta,
                                onRequestPermission = onRequestPermission,
                                onRetryCapture = onRetryCapture,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        // Full-bleed on phones; on wide/short windows the band is
                        // width-limited by the height it needs, so the readout can
                        // never be starved by an over-wide arc.
                        val meterW = minOf(innerW, (assemblyAvailH - READOUT_FLOOR_DP) * 1.6f)
                            .coerceAtLeast(MIN_METER_WIDTH_DP)
                        MeterAssembly(
                            state = state,
                            largeText = false,
                            onRetryCapture = onRetryCapture,
                            availableHeightDp = assemblyAvailH,
                            readoutCapDp = MeterAssemblyLayout.MAX_READOUT_DP,
                            // In a short slot the arc flattens slightly so the
                            // READOUT keeps its full stack; on tall screens the
                            // dial keeps its deeper aspect.
                            minGaugeAspect = if (assemblyAvailH - innerW / 1.6f >= 100f) {
                                MeterAssemblyLayout.MIN_GAUGE_ASPECT
                            } else {
                                1.85f
                            },
                            cornerRadiusDp = 0f,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(meterW.dp),
                        )
                    }
                    Spacer(Modifier.height(GAP_TO_BLOCK_DP.dp))
                    // ── The deck: edge to edge, down to the bottom of the app ─
                    ControlDeck(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        Spacer(Modifier.height(InstrumentTokens.gutter))
                        ControlBlock(
                            mode = state.mode,
                            presetName = state.presetName,
                            targetLabels = state.targetLabels,
                            activeStringIndex = state.activeStringIndex,
                            stringLocked = state.stringLocked,
                            concertPitchHz = state.concertPitchHz,
                            accidentalSpelling = state.accidentalSpelling,
                            compact = compactBlock,
                            onModeSelect = onModeSelect,
                            onStringSelect = onStringSelect,
                            onOpenSheet = onOpenSheet,
                            onOpenSettings = onOpenSettings,
                            onAutoSelectionChange = onAutoSelectionChange,
                        )
                        Spacer(Modifier.height(InstrumentTokens.gutter + bottomInset.dp))
                    }
                }
            }
        }
    }
}

/** Status band height (the lamp + one short line). */
private const val STATUS_BAND_DP = 22f
private const val GAP_TO_BLOCK_DP = 0f
/** Below this the meter stops being the centrepiece → accessible fallback. */
private const val MIN_FIXED_ASSEMBLY_DP = 240f
/** Assembly height that keeps the dial AND the full readout stack intact. */
private const val MIN_ASSEMBLY_FULL_DP = 330f
/** Height the readout needs for its full stack (note, caption, cents, hint). */
private const val READOUT_FLOOR_DP = 112f
private const val MIN_METER_WIDTH_DP = 260f

/**
 * The control rows: mode → active tuning → auto-select → string targets →
 * configuration. Rows on one surface; whitespace does the grouping.
 *
 * This block takes only the slices it renders (no pitch fields), so the
 * 20 Hz measurement ticker never recomposes the deck — pitch updates stay
 * local to the meter/readout. The mode-specific body fades in on change; the
 * outgoing body is removed immediately (no overlap, no queued entrance).
 */
@Composable
private fun ControlBlock(
    mode: TunerMode,
    presetName: String?,
    targetLabels: List<String>,
    activeStringIndex: Int?,
    stringLocked: Boolean,
    concertPitchHz: Double,
    accidentalSpelling: Spelling,
    compact: Boolean,
    onModeSelect: (TunerMode) -> Unit,
    onStringSelect: (Int) -> Unit,
    onOpenSheet: () -> Unit,
    onOpenSettings: () -> Unit,
    onAutoSelectionChange: (Boolean) -> Unit,
) {
    val p = InstrumentTheme.palette
    val instrument = mode == TunerMode.INSTRUMENT
    val hasStrings = targetLabels.isNotEmpty()
    val a4 = "A4 = ${concertPitchHz.toIntIfWholeOrOneDecimal()} Hz"
    val spellingWord = when (accidentalSpelling) {
        Spelling.SHARP -> "Sharps ♯"
        Spelling.FLAT -> "Flats ♭"
        else -> "Naturals"
    }
    Column(Modifier.fillMaxWidth()) {
        MechanicalModeSelector(
            mode = mode,
            onModeSelect = onModeSelect,
            largeText = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = InstrumentTokens.gutter),
        )
        Spacer(Modifier.height(ControlDeckLayout.GAP_TIGHT_DP.dp))
        FadeInOnChange(key = mode, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                if (instrument) {
                    TuningSummaryRow(
                        name = presetName ?: "Standard",
                        onClick = onOpenSheet,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (hasStrings) {
                        Spacer(Modifier.height(ControlDeckLayout.GAP_TIGHT_DP.dp))
                        ToggleRow(
                            label = "Auto-select string",
                            checked = !stringLocked,
                            onCheckedChange = onAutoSelectionChange,
                            modifier = Modifier.fillMaxWidth(),
                            tag = "resonance.autoSelection",
                        )
                        Spacer(Modifier.height(ControlDeckLayout.GAP_GROUP_DP.dp))
                        StringTargets(
                            targetLabels = targetLabels,
                            activeStringIndex = activeStringIndex,
                            stringLocked = stringLocked,
                            onStringSelect = onStringSelect,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = InstrumentTokens.gutter),
                        )
                    }
                    if (!compact) {
                        Spacer(Modifier.height(ControlDeckLayout.GAP_TIGHT_DP.dp))
                        ConfigSummaryRow(
                            label = "Reference pitch",
                            value = a4,
                            onClick = onOpenSettings,
                            valueTag = "resonance.concert",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    // Chromatic keeps only what applies to it: one calibration entry
                    // and a short, honest note. No instrument-only rows, no blank panel.
                    ChromaticConfigRow(
                        headline = "Calibration & note names",
                        a4 = a4,
                        spelling = spellingWord,
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(ControlDeckLayout.GAP_TIGHT_DP.dp))
                    Text(
                        text = "Chromatic mode tunes any note. Instrument tunings apply in Instrument mode.",
                        style = InstrumentType.caption,
                        color = p.textSecondary,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = InstrumentTokens.gutter),
                    )
                }
            }
        }
    }
}

/** The live signal state only — one small line above the meter. */
@Composable
private fun StatusStrip(phase: SignalPhase, statusText: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicStatusLine(phase = phase, statusText = statusText)
    }
}

/** Permission / capture-error recovery panel (mounted in the meter slot). */
@Composable
private fun CapturePanel(
    state: MainScreenUiState,
    onPermissionCta: () -> Unit,
    onRequestPermission: () -> Unit,
    onRetryCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    RecessedPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("resonance.permission.banner"),
    ) {
        Column(Modifier.padding(InstrumentTokens.surfacePad)) {
            Text(
                text = if (state.captureError) "Microphone unavailable" else "Microphone access",
                style = InstrumentType.title,
                color = p.textPrimary,
            )
            Spacer(Modifier.height(InstrumentTokens.space1))
            Text(
                text = if (state.captureError) {
                    "Check your input, then retry."
                } else {
                    state.permissionHint.orEmpty()
                },
                style = InstrumentType.body,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(InstrumentTokens.space3))
            if (state.captureError) {
                InstrumentKey(
                    onClick = onRetryCapture,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("resonance.retry"),
                ) { Text("Retry microphone", style = InstrumentType.label) }
            } else {
                InstrumentKey(
                    onClick = {
                        if (state.permissionCtaOpensSettings) onPermissionCta() else onRequestPermission()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("resonance.permission"),
                ) { Text(state.permissionCtaText, style = InstrumentType.label) }
            }
        }
    }
}

/**
 * Accessible fallback for exceptionally constrained layouts / large
 * accessibility fonts: the same instrument, scrollable, nothing clipped.
 */
@Composable
private fun FallbackLayout(
    state: MainScreenUiState,
    granted: Boolean,
    noteSize: androidx.compose.ui.unit.TextUnit,
    centsSize: androidx.compose.ui.unit.TextUnit,
    largeText: Boolean,
    compactBlock: Boolean,
    bottomInset: Float,
    onModeSelect: (TunerMode) -> Unit,
    onStringSelect: (Int) -> Unit,
    onPermissionCta: () -> Unit,
    onRetryCapture: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSheet: () -> Unit,
    onOpenSettings: () -> Unit,
    onAutoSelectionChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("resonance.scroll"),
        verticalArrangement = Arrangement.spacedBy(InstrumentTokens.space3),
    ) {
        if (!granted || state.captureError) {
            CapturePanel(
                state = state,
                onPermissionCta = onPermissionCta,
                onRequestPermission = onRequestPermission,
                onRetryCapture = onRetryCapture,
                modifier = Modifier.padding(horizontal = InstrumentTokens.gutter),
            )
        } else {
            AnalogTuningMeter(
                position = state.meterPosition,
                inTune = state.inTune,
                clamped = state.meterClamped,
                active = state.meterActive,
                compact = largeText,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            )
            NoteReadout(
                state = state,
                noteSize = noteSize,
                centsSize = centsSize,
                largeText = largeText,
                onRetryCapture = onRetryCapture,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ControlDeck(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 0.dp),
        ) {
            Spacer(Modifier.height(InstrumentTokens.gutter))
            ControlBlock(
                mode = state.mode,
                presetName = state.presetName,
                targetLabels = state.targetLabels,
                activeStringIndex = state.activeStringIndex,
                stringLocked = state.stringLocked,
                concertPitchHz = state.concertPitchHz,
                accidentalSpelling = state.accidentalSpelling,
                compact = compactBlock,
                onModeSelect = onModeSelect,
                onStringSelect = onStringSelect,
                onOpenSheet = onOpenSheet,
                onOpenSettings = onOpenSettings,
                onAutoSelectionChange = onAutoSelectionChange,
            )
            Spacer(Modifier.height(InstrumentTokens.gutter + bottomInset.dp))
        }
    }
}

@Composable
private fun MicStatusLine(
    phase: SignalPhase,
    statusText: String,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    // The lamp agrees with the signal phase — sage only while tracking, amber
    // while waiting, dim when lost, red on a capture error. The adjacent text
    // carries the same state, so colour is never the only carrier. The colour
    // settles between phases; the lit/unlit glow stays immediate.
    val color: Color
    val lit: Boolean
    when (phase) {
        SignalPhase.TRACKING -> { color = p.lampGreen; lit = true }
        SignalPhase.WAITING, SignalPhase.HOLDING -> { color = p.lampAmber; lit = true }
        SignalPhase.LOST -> { color = p.lampAmber; lit = false }
        SignalPhase.MIC_OFF -> { color = p.lampOff; lit = false }
        SignalPhase.MIC_ERROR -> { color = p.lampRed; lit = true }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JewelLamp(lit = lit, color = animatedLampColor(color), size = 8.dp)
        Spacer(Modifier.size(InstrumentTokens.space2))
        Text(
            text = statusText,
            style = InstrumentType.caption,
            modifier = Modifier.testTag("resonance.micStatus"),
        )
    }
}

private fun Double.toIntIfWholeOrOneDecimal(): String {
    val tenths = (this * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}
