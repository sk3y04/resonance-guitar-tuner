package dev.anima.resonance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.ui.instrument.AnalogTuningMeter
import dev.anima.resonance.ui.instrument.InstrumentFaceplate
import dev.anima.resonance.ui.instrument.InstrumentKey
import dev.anima.resonance.ui.instrument.GearIcon
import dev.anima.resonance.ui.instrument.InstrumentStringSelector
import dev.anima.resonance.ui.instrument.InstrumentTheme
import dev.anima.resonance.ui.instrument.InstrumentType
import dev.anima.resonance.ui.instrument.JewelLamp
import dev.anima.resonance.ui.instrument.MechanicalModeSelector
import dev.anima.resonance.ui.instrument.MeterAssembly
import dev.anima.resonance.ui.instrument.MeterAssemblyLayout
import dev.anima.resonance.ui.instrument.NoteReadout
import dev.anima.resonance.ui.instrument.RecessedPanel
import dev.anima.resonance.ui.instrument.WoodenEnclosure
import kotlin.math.roundToInt

/**
 * The main instrument — ONE fixed, non-scrolling device at normal
 * phone sizes and default font scale. Structure:
 *
 *   enclosure (wooden body) → faceplate (shared inset surface) →
 *   components mounted on it: label strip, analog meter (flexible space),
 *   note readout, mode selector, string keys, TUNINGS & SETTINGS key.
 *
 * Configuration lives in [TuningsOverlay], opened by the labeled key.
 * There is no app header; tuning name, capture state and A4 reference sit
 * in a compact label strip on the faceplate. System insets are respected.
 *
 * Exceptionally constrained layouts (very short screens) and large
 * accessibility font scales get an explicit scrollable fallback
 * (resonance.scroll) instead of clipped content.
 *
 * Semantic/test tags: resonance.note, resonance.cents, resonance.dial,
 * resonance.direction, resonance.hint, resonance.micStatus,
 * resonance.mode(+.chromatic/.instrument), resonance.string.<i>,
 * resonance.concert, resonance.permission(.banner), resonance.preset,
 * resonance.openSheet, resonance.retry, resonance.start, resonance.scroll
 * (fallback only).
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
    modifier: Modifier = Modifier,
) {
    val granted = state.permission == MicPermissionState.GRANTED
    val fontScale = LocalDensity.current.fontScale
    val largeText = fontScale > 1.3f
    // Cap the *rendered* size, not the sp value, so asking for larger text
    // never shrinks the note; balanced against the meter.
    val noteSize = (96f / fontScale).coerceAtMost(64f).sp
    val centsSize = (56f / fontScale).coerceAtMost(34f).sp

    Box(modifier = modifier.fillMaxSize()) {
        WoodenEnclosure(Modifier.matchParentSize())
        // One restrained outer enclosure inset + system insets.
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
        ) {
            InstrumentFaceplate(Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 480.dp)
                        .fillMaxSize()
                        .padding(androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 12.dp)),
                ) {
                    val innerW = maxWidth
                    val innerH = maxHeight
                    // One predictable vertical composition — status strip,
                    // DOMINANT meter, compact attached readout, compact mode
                    // selector, compact string strip (instrument only), settings,
                    // ONE modest bottom enclosure margin. Small consistent gaps;
                    // no SpaceBetween/SpaceEvenly, no vertical weight on the
                    // readout or string controls. The meter is sized to its
                    // coherent width-bound maximum (deeper 100° dial); surplus
                    // beyond that is a single wood enclosure margin, never an
                    // empty internal panel or stretched buttons.
                    val hasStrings = state.targetLabels.isNotEmpty()
                    val n = state.targetLabels.size
                    val singleRow = hasStrings && n in 1..8 &&
                        innerW >= 48.dp * n + 4.dp * (n - 1)
                    val stringsH = when {
                        !hasStrings -> 0.dp
                        singleRow -> 69.dp // caption + gap + one 52dp row
                        else -> 129.dp // caption + gap + two 56dp rows + 6dp gap
                    }
                    val controlsNaturalH = 56.dp + stringsH + 48.dp
                    val availableAssemblyH = innerH - 24.dp - controlsNaturalH - 66.dp
                    // Fixed layout when the gauge has real room; otherwise an
                    // accessible scrolling fallback (large fonts / tiny screens).
                    val fallback = largeText || availableAssemblyH < 160.dp
                    // The wide landscape dial frees vertical space. Redistribute
                    // it as balanced breathing room between the control groups
                    // (dynamic gaps, capped) plus a modest coherent deepening of the
                    // dial — never one large empty footer band, never stretched keys.
                    val nGaps = if (hasStrings) 4 else 3
                    val baseGap = 14f
                    val gaugeNatural = innerW.value / MeterAssemblyLayout.GAUGE_ASPECT
                    val assemblyNatural =
                        gaugeNatural + MeterAssemblyLayout.MAX_READOUT_DP + 1.5f
                    val naturalH =
                        24f + assemblyNatural + controlsNaturalH.value + baseGap * nGaps + 24f
                    val spare = (innerH.value - naturalH).coerceAtLeast(0f)
                    // The dial deepens coherently first; the remainder becomes
                    // balanced breathing room between groups (fewer groups in
                    // chromatic => larger gaps). Residual is a modest wood margin.
                    val minAspect =
                        if (hasStrings) MeterAssemblyLayout.MIN_GAUGE_ASPECT else 1.0f
                    val gaugeMaxGrow = innerW.value / minAspect - gaugeNatural
                    val gaugeGrow = spare.coerceIn(0f, gaugeMaxGrow)
                    val remSpare = (spare - gaugeGrow).coerceAtLeast(0f)
                    val maxGapExtra = if (hasStrings) 24f else 44f
                    val gapExtra = (remSpare / nGaps).coerceAtMost(maxGapExtra)
                    val sectionGap = (baseGap + gapExtra).dp
                    val assemblyAvailH = assemblyNatural + gaugeGrow

                    if (fallback) {
                        FallbackLayout(
                            state = state,
                            granted = granted,
                            noteSize = noteSize,
                            centsSize = centsSize,
                            largeText = largeText,
                            onModeSelect = onModeSelect,
                            onStringSelect = onStringSelect,
                            onPermissionCta = onPermissionCta,
                            onRetryCapture = onRetryCapture,
                            onRequestPermission = onRequestPermission,
                            onOpenSheet = onOpenSheet,
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(sectionGap),
                        ) {
                            LabelStrip(state, onOpenSheet)
                            if (!granted || state.captureError) {
                                CapturePanel(
                                    state = state,
                                    onPermissionCta = onPermissionCta,
                                    onRequestPermission = onRequestPermission,
                                    onRetryCapture = onRetryCapture,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                // The meter assembly is the primary instrument and
                                // the only section sized to absorb spare height —
                                // up to its coherent arc-filling maximum. The dark
                                // readout stays compact in BOTH modes (no expansion
                                // when strings are hidden).
                                MeterAssembly(
                                    state = state,
                                    largeText = false,
                                    onRetryCapture = onRetryCapture,
                                    availableHeightDp = minOf(
                                        assemblyAvailH,
                                        availableAssemblyH.value,
                                    ),
                                    readoutCapDp = MeterAssemblyLayout.MAX_READOUT_DP,
                                    minGaugeAspect = minAspect,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            MechanicalModeSelector(
                                mode = state.mode,
                                onModeSelect = onModeSelect,
                                largeText = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (hasStrings) {
                                InstrumentStringSelector(
                                    targetLabels = state.targetLabels,
                                    activeStringIndex = state.activeStringIndex,
                                    stringLocked = state.stringLocked,
                                    onStringSelect = onStringSelect,
                                )
                            }
                            InstrumentKey(
                                onClick = onOpenSheet,
                                bevel = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("resonance.openSheet"),
                            ) {
                                GearIcon()
                                Spacer(Modifier.size(8.dp))
                                Text("TUNINGS & SETTINGS", style = InstrumentType.keyLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Header: preset selector (with chevron), live status, A4 reference —
 *  one crisp horizontal baseline, balanced spacing, tabular reference pitch. */
@Composable
private fun LabelStrip(state: MainScreenUiState, onOpenSheet: () -> Unit = {}) {
    val p = InstrumentTheme.palette
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.presetName ?: "Chromatic",
                style = InstrumentType.engravedStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(onClick = onOpenSheet)
                    .testTag("resonance.preset")
                    .semantics { heading() },
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "▾",
                style = InstrumentType.engraved.copy(fontSize = 11.sp, color = p.brass),
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        MicStatusLine(
            text = state.micStatusText,
            isError = state.captureError,
        )
        Spacer(Modifier.size(12.dp))
        ConcertPitchText(hz = state.concertPitchHz)
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
        Column(Modifier.padding(14.dp)) {
            Text(
                text = if (state.captureError) "Microphone unavailable" else "Microphone access",
                style = MaterialTheme.typography.titleSmall.copy(color = p.ivory),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.captureError) {
                    "Check your input, then retry."
                } else {
                    state.permissionHint.orEmpty()
                },
                style = InstrumentType.readoutSmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(10.dp))
            if (state.captureError) {
                InstrumentKey(
                    onClick = onRetryCapture,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("resonance.retry"),
                ) { Text("Retry microphone", style = InstrumentType.keyLabel) }
            } else {
                InstrumentKey(
                    onClick = {
                        if (state.permissionCtaOpensSettings) onPermissionCta() else onRequestPermission()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("resonance.permission"),
                ) { Text(state.permissionCtaText, style = InstrumentType.keyLabel) }
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
    onModeSelect: (TunerMode) -> Unit,
    onStringSelect: (Int) -> Unit,
    onPermissionCta: () -> Unit,
    onRetryCapture: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSheet: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("resonance.scroll"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LabelStrip(state, onOpenSheet)
        if (!granted || state.captureError) {
            CapturePanel(
                state = state,
                onPermissionCta = onPermissionCta,
                onRequestPermission = onRequestPermission,
                onRetryCapture = onRetryCapture,
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
        MechanicalModeSelector(
            mode = state.mode,
            onModeSelect = onModeSelect,
            largeText = largeText,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.targetLabels.isNotEmpty()) {
            InstrumentStringSelector(
                targetLabels = state.targetLabels,
                activeStringIndex = state.activeStringIndex,
                stringLocked = state.stringLocked,
                onStringSelect = onStringSelect,
            )
        }
        InstrumentKey(
            onClick = onOpenSheet,
            bevel = true,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("resonance.openSheet"),
        ) {
            GearIcon()
            Spacer(Modifier.size(8.dp))
            Text("TUNINGS & SETTINGS", style = InstrumentType.keyLabel)
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun MicStatusLine(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    val active = (text == "Listening" || text == "Tracking") && !isError
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JewelLamp(
            lit = active || isError,
            color = if (isError) p.lampRed else p.lampGreen,
            size = 10.dp,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = text,
            style = InstrumentType.readoutSmall,
            modifier = Modifier.testTag("resonance.micStatus"),
        )
    }
}

@Composable
private fun ConcertPitchText(
    hz: Double,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "A4 ${hz.toIntIfWholeOrOneDecimal()} Hz",
        style = InstrumentType.readoutSmall,
        modifier = modifier
            .testTag("resonance.concert")
            .semantics {
                contentDescription =
                    "Reference pitch A4 ${hz.toIntIfWholeOrOneDecimal()} hertz"
            },
    )
}

private fun Double.toIntIfWholeOrOneDecimal(): String {
    val tenths = (this * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}