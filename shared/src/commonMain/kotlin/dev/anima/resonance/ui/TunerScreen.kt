package dev.anima.resonance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.SignalPhase
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.music.Spelling
import dev.anima.resonance.ui.instrument.AnalogTuningMeter
import dev.anima.resonance.ui.instrument.InstrumentFaceplate
import dev.anima.resonance.ui.instrument.InstrumentKey
import dev.anima.resonance.ui.instrument.GearIcon
import dev.anima.resonance.ui.instrument.InstrumentStringSelector
import dev.anima.resonance.ui.instrument.InstrumentTheme
import dev.anima.resonance.ui.instrument.InstrumentTokens
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
                    // Reserve the string rail in BOTH modes (from the
                    // selected preset's string count) so the meter/readout
                    // geometry and the config-entry position are IDENTICAL when
                    // switching modes. Chromatic leaves the rail as modest
                    // intentional negative space rather than resizing the meter.
                    val railN = if (hasStrings) state.targetLabels.size else state.railStringCount
                    val railSingleRow = railN in 1..8 &&
                        innerW >= 48.dp * railN + 4.dp * (railN - 1)
                    val stringsH = when {
                        railN <= 0 -> 0.dp
                        railSingleRow -> 69.dp // caption + gap + one 52dp row
                        else -> 129.dp // caption + gap + two 56dp rows + 6dp gap
                    }
                    val configH = 64.dp
                    val controlsNaturalH = 56.dp + stringsH + configH
                    val availableAssemblyH = innerH - 24.dp - controlsNaturalH - 66.dp
                    // Fixed layout when the gauge has real room; otherwise an
                    // accessible scrolling fallback (large fonts / tiny screens).
                    val fallback = largeText || availableAssemblyH < 160.dp
                    // One predictable stack — status, meter+readout, mode,
                    // string rail (or reserved space), config entry. Spare height
                    // deepens the dial coherently first, then becomes balanced
                    // breathing room; the residual is a modest enclosure margin.
                    val nGaps = if (stringsH > 0.dp) 4 else 3
                    val baseGap = 14f
                    val gaugeNatural = innerW.value / MeterAssemblyLayout.GAUGE_ASPECT
                    val assemblyNatural =
                        gaugeNatural + MeterAssemblyLayout.MAX_READOUT_DP + 1.5f
                    val naturalH =
                        24f + assemblyNatural + controlsNaturalH.value + baseGap * nGaps + 24f
                    val spare = (innerH.value - naturalH).coerceAtLeast(0f)
                    val minAspect = MeterAssemblyLayout.MIN_GAUGE_ASPECT
                    val gaugeMaxGrow = innerW.value / minAspect - gaugeNatural
                    val gaugeGrow = spare.coerceIn(0f, gaugeMaxGrow)
                    val remSpare = (spare - gaugeGrow).coerceAtLeast(0f)
                    val gapExtra = (remSpare / nGaps).coerceAtMost(24f)
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
                            LabelStrip(state)
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
                            } else if (stringsH > 0.dp) {
                                // Chromatic: reserve the rail as modest negative
                                // space so the config entry stays put.
                                Spacer(Modifier.fillMaxWidth().height(stringsH))
                            }
                            ConfigurationEntry(
                                state = state,
                                onOpenSheet = onOpenSheet,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Header: a minimal status strip — the live signal state only. Preset,
 *  reference pitch and spelling live in the compact configuration entry below
 *  (no duplicated config, no top bar full of controls). */
@Composable
private fun LabelStrip(state: MainScreenUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicStatusLine(state = state)
    }
}

/**
 * The compact, informative configuration entry that replaces the oversized
 * "TUNINGS & SETTINGS" button. The whole area is the tap target (the gear icon
 * is decorative, never the only hit area). It adapts to the actual settings:
 * Instrument shows the preset name + note sequence + A4; Chromatic shows
 * "Tuner settings" + A4 + accidental preference (the saved instrument preset
 * does NOT constrain chromatic detection, so it is not implied here).
 */
@Composable
private fun ConfigurationEntry(
    state: MainScreenUiState,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    val instrument = state.mode == TunerMode.INSTRUMENT
    val title = if (instrument) (state.presetName ?: "Standard") else "Tuner settings"
    val notes = state.targetLabels.joinToString(" ") { it.dropLast(1) }
    val a4 = "A4 ${state.concertPitchHz.toIntIfWholeOrOneDecimal()} Hz"
    val spellingWord = when (state.accidentalSpelling) {
        Spelling.SHARP -> "Sharps"
        Spelling.FLAT -> "Flats"
        else -> "Naturals"
    }
    // Whole-area tap target with press feedback. The click is exposed via
    // semantics onClick (NOT a merging clickable/Surface) so the preset and
    // reference-pitch child tags stay findable in the merged semantics tree.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(InstrumentTokens.cornerMd))
            .background(if (pressed) p.walnutLight else p.recess)
            .pointerInput(onOpenSheet) {
                detectTapGestures(
                    onPress = {
                        val press = PressInteraction.Press(it)
                        interaction.tryEmit(press)
                        tryAwaitRelease()
                        interaction.tryEmit(PressInteraction.Release(press))
                    },
                    onTap = { onOpenSheet() },
                )
            }
            .testTag("resonance.openSheet")
            .semantics {
                contentDescription = "Tunings and settings. $title. $a4."
                onClick(label = "Open tunings and settings") { onOpenSheet(); true }
            },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = InstrumentType.keyLabel.copy(color = p.ivory),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("resonance.preset"),
                )
                GearIcon(modifier = Modifier.size(16.dp), color = p.brass)
            }
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (instrument) {
                    Text(
                        text = notes,
                        style = InstrumentType.readoutSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clearAndSetSemantics {},
                    )
                }
                Text(
                    text = a4,
                    style = InstrumentType.readoutSmall,
                    maxLines = 1,
                    modifier = Modifier
                        .testTag("resonance.concert")
                        .semantics { contentDescription = "Reference pitch $a4" },
                )
                if (!instrument) {
                    Text(
                        text = " · $spellingWord",
                        style = InstrumentType.readoutSmall,
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
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
        ConfigurationEntry(
            state = state,
            onOpenSheet = onOpenSheet,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun MicStatusLine(
    state: MainScreenUiState,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    // The lamp agrees with the signal phase — green only while tracking,
    // amber while waiting, dim when lost, red on a capture error. Never colour
    // alone: the adjacent text carries the same state.
    val color: Color
    val lit: Boolean
    when (state.signalPhase) {
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
        JewelLamp(lit = lit, color = color, size = 10.dp)
        Spacer(Modifier.size(6.dp))
        Text(
            text = state.micStatusText,
            style = InstrumentType.readoutSmall,
            modifier = Modifier.testTag("resonance.micStatus"),
        )
    }
}

private fun Double.toIntIfWholeOrOneDecimal(): String {
    val tenths = (this * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}