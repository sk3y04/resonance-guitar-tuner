package dev.anima.resonance.ui

import androidx.compose.foundation.layout.Arrangement
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
import dev.anima.resonance.ui.instrument.InstrumentStringSelector
import dev.anima.resonance.ui.instrument.InstrumentTheme
import dev.anima.resonance.ui.instrument.InstrumentType
import dev.anima.resonance.ui.instrument.JewelLamp
import dev.anima.resonance.ui.instrument.MechanicalModeSelector
import dev.anima.resonance.ui.instrument.MeterAssembly
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
                        .fillMaxSize()
                        .widthIn(max = 480.dp)
                        .padding(12.dp),
                ) {
                    val innerW = maxWidth
                    val innerH = maxHeight
                    // Fixed-height budget of the mounted components.
                    val stringRows = when {
                        state.targetLabels.isEmpty() -> 0
                        innerW < 480.dp || state.targetLabels.size > 6 ->
                            (state.targetLabels.size + 2) / 3
                        else -> 1
                    }
                    val stringsH = if (stringRows == 0) 0.dp else (stringRows * 58 + (stringRows - 1) * 6).dp
                    // The measurement assembly is height-aware — its gauge
                    // shrinks to fit short screens and stays natural on tall
                    // ones; the lower controls take any surplus height.
                    val controlsMinH = 56.dp + stringsH + 52.dp + 20.dp
                    val availableAssemblyH = innerH - 24.dp - controlsMinH - 40.dp
                    // Main-screen guarantee: fixed layout when the gauge has
                    // real room at normal scale; otherwise a scrolling fallback.
                    val fallback = largeText || availableAssemblyH < 120.dp

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
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LabelStrip(state)
                            // The measurement assembly (gauge + readout) sits at
                            // its natural height directly under the label strip.
                            if (!granted || state.captureError) {
                                CapturePanel(
                                    state = state,
                                    onPermissionCta = onPermissionCta,
                                    onRequestPermission = onRequestPermission,
                                    onRetryCapture = onRetryCapture,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                MeterAssembly(
                                    state = state,
                                    largeText = false,
                                    onRetryCapture = onRetryCapture,
                                    availableHeightDp = availableAssemblyH.value,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            // The lower controls absorb the surplus height:
                            // mode + strings grouped compactly, settings anchored
                            // to the bottom of the faceplate (nothing squeezed,
                            // no empty bands around the meter).
                            Column(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            ) {
                                Spacer(Modifier.weight(1f))
                                MechanicalModeSelector(
                                    mode = state.mode,
                                    onModeSelect = onModeSelect,
                                    largeText = false,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (state.targetLabels.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    InstrumentStringSelector(
                                        targetLabels = state.targetLabels,
                                        activeStringIndex = state.activeStringIndex,
                                        stringLocked = state.stringLocked,
                                        onStringSelect = onStringSelect,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                InstrumentKey(
                                    onClick = onOpenSheet,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("resonance.openSheet"),
                                ) {
                                    Text("TUNINGS & SETTINGS", style = InstrumentType.keyLabel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Compact faceplate label strip: tuning name, capture state, A4 reference. */
@Composable
private fun LabelStrip(state: MainScreenUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.presetName ?: "Chromatic",
            style = InstrumentType.engravedStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .testTag("resonance.preset")
                .semantics { heading() },
        )
        MicStatusLine(
            text = state.micStatusText,
            isError = state.captureError,
        )
        Spacer(Modifier.size(10.dp))
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
        LabelStrip(state)
        if (!granted || state.captureError) {
            CapturePanel(
                state = state,
                onPermissionCta = onPermissionCta,
                onRequestPermission = onRequestPermission,
                onRetryCapture = onRetryCapture,
            )
        } else {
            state.statusHint?.let { hint ->
                Text(
                    text = hint,
                    style = InstrumentType.readoutSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resonance.hint"),
                )
            }
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
            if (granted && !state.captureActive) {
                InstrumentKey(
                    onClick = onRetryCapture,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .align(Alignment.CenterHorizontally)
                        .testTag("resonance.start"),
                ) { Text("Start listening", style = InstrumentType.keyLabel) }
            }
            NoteReadout(
                state = state,
                noteSize = noteSize,
                centsSize = centsSize,
                largeText = largeText,
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("resonance.openSheet"),
        ) { Text("TUNINGS & SETTINGS", style = InstrumentType.keyLabel) }
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
    val listening = text == "Listening" && !isError
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JewelLamp(
            lit = listening || isError,
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