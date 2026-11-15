package dev.anima.resonance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.music.Spelling
import dev.anima.resonance.presentation.CustomEditorUiState
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.PresetRowUi
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.ui.instrument.AnalogTuningMeter
import dev.anima.resonance.ui.instrument.CalibrationControls
import dev.anima.resonance.ui.instrument.InstrumentKey
import dev.anima.resonance.ui.instrument.InstrumentStringSelector
import dev.anima.resonance.ui.instrument.InstrumentTheme
import dev.anima.resonance.ui.instrument.InstrumentType
import dev.anima.resonance.ui.instrument.JewelLamp
import dev.anima.resonance.ui.instrument.MechanicalModeSelector
import dev.anima.resonance.ui.instrument.NoteReadout
import dev.anima.resonance.ui.instrument.RecessedPanel
import dev.anima.resonance.ui.instrument.SpellingKeys
import dev.anima.resonance.ui.instrument.WoodenEnclosure
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Main instrument screen (analog redesign). Renders the tested
 * [MainScreenUiState] only: no measurement logic lives here. The screen is
 * a walnut enclosure carrying an ivory analog meter, a recessed note
 * readout, mechanical mode/string keys and a milled calibration sheet.
 *
 * Layout is one vertically scrollable instrument surface. A scrolling root
 * is deliberate: at 320 dp / large font sizes the meter, readout, strings,
 * mode and the full tuning sheet cannot all fit at once; hiding the lower
 * controls behind a clipped, non-scrollable column was a real usability
 * defect.
 *
 * Semantic/test tags (unchanged contract): resonance.scroll, resonance.note,
 * resonance.cents, resonance.dial, resonance.direction, resonance.hint,
 * resonance.micStatus, resonance.mode(+.chromatic/.instrument),
 * resonance.string.<i>, resonance.settings, resonance.concert,
 * resonance.permission, resonance.preset, resonance.sheet.*.
 */
@Composable
fun TunerScreen(
    state: MainScreenUiState,
    onOpenSettings: () -> Unit,
    onOpenPresets: () -> Unit,
    onModeSelect: (TunerMode) -> Unit,
    onStringSelect: (Int) -> Unit,
    onPermissionCta: () -> Unit,
    onRetryCapture: () -> Unit,
    onRequestPermission: () -> Unit = {},
    onConcertStep: (Boolean) -> Unit = {},
    onConcertStepCoarse: (Boolean) -> Unit = {},
    onPresetSelect: (String) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onAddTuning: () -> Unit = {},
    onEditTuning: (String) -> Unit = {},
    onDeleteTuning: (String) -> Unit = {},
    onSpellingSelect: (Spelling) -> Unit = {},
    onImportRequested: () -> Unit = {},
    onExportRequested: () -> Unit = {},
    editor: CustomEditorUiState? = null,
    onEditorNameChange: (String) -> Unit = {},
    onEditorNoteCycle: (Int, Boolean) -> Unit = { _, _ -> },
    onEditorOctaveCycle: (Int, Boolean) -> Unit = { _, _ -> },
    onEditorRemoveString: (Int) -> Unit = {},
    onEditorAddString: () -> Unit = {},
    onEditorSave: () -> Unit = {},
    onEditorCancel: () -> Unit = {},
    showSettingsButton: Boolean = true,
    showPresetsButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    val granted = state.permission == MicPermissionState.GRANTED
    val fontScale = LocalDensity.current.fontScale
    // Adaptive glyphs: system font scaling still applies, but an uncapped
    // 96sp note would overflow narrow screens at 2x accessibility text.
    val largeText = fontScale > 1.3f
    // Cap the *rendered* size, not the sp value: scaling from 96sp to 64sp
    // at 1.4x would make the note smaller as the user asks for larger text.
    val noteSize = (128f / fontScale).coerceAtMost(96f).sp
    val centsSize = (72f / fontScale).coerceAtMost(44f).sp
    val scrollState = rememberScrollState()
    val tuningsRequester = remember { BringIntoViewRequester() }
    val editorRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val editorOpen = editor != null
    val editorId = editor?.editingId
    LaunchedEffect(editorOpen, editorId) {
        if (editorOpen) {
            // Wait for the editor to be measured, then make it the focus of
            // the scroll surface (it is inserted above the long sheet).
            androidx.compose.runtime.withFrameNanos { }
            editorRequester.bringIntoView()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // The enclosure is static artwork: it never recomposes with the
        // pitch ticker (grain paths are cached per size inside the Canvas).
        WoodenEnclosure(Modifier.matchParentSize())

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("resonance.scroll"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Header: maker's mark, tuning name, hardware shortcuts ─────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    // Decorative maker's mark; dropped at large font scales
                    // so the note readout stays above the fold on small screens.
                    if (!largeText) {
                        Text(
                            text = "RESONANCE",
                            style = InstrumentType.engraved,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                    Text(
                        text = state.presetName ?: "Chromatic",
                        style = MaterialTheme.typography.titleMedium.copy(color = p.ivory),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .testTag("resonance.preset")
                            .semantics { heading() },
                    )
                }
                if (state.presetRows.isNotEmpty()) {
                    InstrumentKey(
                        onClick = { scope.launch { tuningsRequester.bringIntoView() } },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("resonance.openTunings"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) { Text("Tunings", style = InstrumentType.keyLabel) }
                    Spacer(Modifier.size(6.dp))
                }
                if (showSettingsButton) {
                    InstrumentKey(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("resonance.settings"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) { Text("Settings", style = InstrumentType.keyLabel) }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Live capture state + reference pitch ──────────────────────
            if (largeText) {
                Column(Modifier.fillMaxWidth()) {
                    MicStatusLine(
                        text = state.micStatusText,
                        isError = state.captureError,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ConcertPitchText(
                        hz = state.concertPitchHz,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MicStatusLine(
                        text = state.micStatusText,
                        isError = state.captureError,
                        modifier = Modifier.weight(1f),
                    )
                    ConcertPitchText(hz = state.concertPitchHz)
                }
            }

            // ── Permission recovery (first run / soft denial / permanent) ─
            if (state.permissionCtaVisible) {
                Spacer(Modifier.height(12.dp))
                RecessedPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resonance.permission.banner"),
                    borderColor = p.brassDark,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = "Microphone access",
                            style = MaterialTheme.typography.titleSmall.copy(color = p.ivory),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.permissionHint.orEmpty(),
                            style = InstrumentType.readoutSmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Spacer(Modifier.height(10.dp))
                        InstrumentKey(
                            onClick = {
                                if (state.permissionCtaOpensSettings) {
                                    onPermissionCta()
                                } else {
                                    onRequestPermission()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("resonance.permission"),
                        ) {
                            Text(state.permissionCtaText, style = InstrumentType.keyLabel)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Signal / capture guidance, directly under the mic state ───
            // First-time clarity: the instruction belongs near "Listening",
            // not below a large meter the user has not learned to read yet.
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
            if (state.captureError) {
                InstrumentKey(
                    onClick = onRetryCapture,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(min = 48.dp)
                        .testTag("resonance.retry"),
                ) { Text("Retry microphone", style = InstrumentType.keyLabel) }
            } else if (granted && !state.captureActive) {
                InstrumentKey(
                    onClick = onRetryCapture,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(min = 48.dp)
                        .testTag("resonance.start"),
                ) { Text("Start listening", style = InstrumentType.keyLabel) }
            }

            // Without permission (or with a failed device) there is no
            // measurement to show. A dead meter implied a live instrument.
            if (granted && !state.captureError) {
                Spacer(Modifier.height(12.dp))

                // ── The analog meter (centerpiece) ────────────────────────
                AnalogTuningMeter(
                    position = state.meterPosition,
                    inTune = state.inTune,
                    clamped = state.meterClamped,
                    active = state.meterActive,
                    compact = largeText,
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                // ── Detected note + measurements ──────────────────────────
                NoteReadout(
                    state = state,
                    noteSize = noteSize,
                    centsSize = centsSize,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Mode: mechanical two-position selector ────────────────────
            MechanicalModeSelector(
                mode = state.mode,
                onModeSelect = onModeSelect,
                largeText = largeText,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.mode == TunerMode.CHROMATIC) {
                Text(
                    text = "Chromatic listens to every note. Instrument mode targets the selected tuning.",
                    style = InstrumentType.readoutSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Target strings (Instrument mode) ──────────────────────────
            if (state.targetLabels.isNotEmpty()) {
                InstrumentStringSelector(
                    targetLabels = state.targetLabels,
                    activeStringIndex = state.activeStringIndex,
                    stringLocked = state.stringLocked,
                    selectedStringIndex = state.selectedStringIndex,
                    onStringSelect = onStringSelect,
                )
                Spacer(Modifier.height(14.dp))
            }

            // ── Optional external "Tunings" entry point (tests/hosts) ─────
            if (showPresetsButton) {
                InstrumentKey(
                    onClick = onOpenPresets,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("resonance.presets"),
                ) { Text("Tunings", style = InstrumentType.keyLabel) }
                Spacer(Modifier.height(10.dp))
            }

            // ── Custom editor (above the sheet) ───────────────────────────
            editor?.let { form ->
                CustomEditorSheet(
                    state = form,
                    onNameChange = onEditorNameChange,
                    onNoteCycle = onEditorNoteCycle,
                    onOctaveCycle = onEditorOctaveCycle,
                    onRemoveString = onEditorRemoveString,
                    onAddString = onEditorAddString,
                    onSave = onEditorSave,
                    onCancel = onEditorCancel,
                    modifier = Modifier
                        .bringIntoViewRequester(editorRequester)
                        .padding(bottom = 12.dp),
                )
            }

            // ── Tunings + reference settings sheet ────────────────────────
            if (state.presetRows.isNotEmpty()) {
                TuningsSheet(
                    state = state,
                    largeText = largeText,
                    tuningsRequester = tuningsRequester,
                    onConcertStep = onConcertStep,
                    onConcertStepCoarse = onConcertStepCoarse,
                    onSpellingSelect = onSpellingSelect,
                    onPresetSelect = onPresetSelect,
                    onToggleFavorite = onToggleFavorite,
                    onEditTuning = onEditTuning,
                    onDeleteTuning = onDeleteTuning,
                    onAddTuning = onAddTuning,
                    onImportRequested = onImportRequested,
                    onExportRequested = onExportRequested,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * The milled calibration/tunings panel. Same rows, actions and semantic
 * contract as the pre-redesign sheet, rendered as instrument hardware.
 */
@Composable
private fun TuningsSheet(
    state: MainScreenUiState,
    largeText: Boolean,
    tuningsRequester: BringIntoViewRequester,
    onConcertStep: (Boolean) -> Unit,
    onConcertStepCoarse: (Boolean) -> Unit,
    onSpellingSelect: (Spelling) -> Unit,
    onPresetSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEditTuning: (String) -> Unit,
    onDeleteTuning: (String) -> Unit,
    onAddTuning: () -> Unit,
    onImportRequested: () -> Unit,
    onExportRequested: () -> Unit,
) {
    val p = InstrumentTheme.palette
    RecessedPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("resonance.sheet"),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "Tunings & settings",
                style = InstrumentType.engravedStrong,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .bringIntoViewRequester(tuningsRequester)
                    .semantics { heading() }
                    .testTag("resonance.sheet.title"),
            )

            // Coarse ±1 Hz + fine ±0.1 Hz reference steppers.
            CalibrationControls(
                concertLabel = state.concertLabel,
                concertError = state.concertError,
                onConcertStep = onConcertStep,
                onConcertStepCoarse = onConcertStepCoarse,
                largeText = largeText,
            )

            if (largeText) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .testTag("resonance.sheet.spelling"),
                ) {
                    Text("Accidentals", style = InstrumentType.readoutSmall)
                    Spacer(Modifier.size(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        SpellingKeys(state.accidentalSpelling, onSpellingSelect)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .testTag("resonance.sheet.spelling"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Accidentals",
                        style = InstrumentType.readoutSmall,
                        modifier = Modifier.weight(1f),
                    )
                    SpellingKeys(state.accidentalSpelling, onSpellingSelect)
                }
            }

            state.presetRows.forEach { row ->
                Surface(
                    onClick = { onPresetSelect(row.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .testTag("resonance.sheet.row.${row.id}")
                        .semantics {
                            contentDescription = "Tuning ${row.name}" +
                                (if (row.selected) ", selected" else "") +
                                (if (row.favorite) ", favorite" else "")
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = if (row.selected) p.walnut else p.recess,
                    border = if (row.selected) {
                        androidx.compose.foundation.BorderStroke(1.dp, p.brass)
                    } else {
                        null
                    },
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = row.name,
                                style = InstrumentType.keyLabel.copy(
                                    color = p.ivory,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (row.selected) {
                                Text(
                                    text = "●",
                                    color = p.brassLight,
                                    style = InstrumentType.readoutSmall,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .clearAndSetSemantics {},
                                )
                            }
                            TextButton(
                                onClick = { onToggleFavorite(row.id) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("resonance.sheet.row.${row.id}.favorite")
                                    .semantics {
                                        contentDescription = if (row.favorite) {
                                            "Remove ${row.name} from favorites"
                                        } else {
                                            "Add ${row.name} to favorites"
                                        }
                                    },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (row.favorite) p.brassLight else p.textMuted,
                                ),
                            ) {
                                Text(
                                    text = if (row.favorite) "★" else "☆",
                                    fontSize = 18.sp,
                                )
                            }
                        }
                        if (largeText) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(row.stringsPreview, style = InstrumentType.readoutSmall)
                                if (!row.builtIn) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        RowEditDelete(row, onEditTuning, onDeleteTuning)
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = row.stringsPreview,
                                    style = InstrumentType.readoutSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!row.builtIn) {
                                    RowEditDelete(row, onEditTuning, onDeleteTuning)
                                }
                            }
                        }
                    }
                }
            }

            if (largeText) {
                // Three equal-width actions cannot show full labels at
                // 2x text; use one full-width action + a two-up row.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SheetAction("+ New", "resonance.sheet.add", onAddTuning, Modifier.fillMaxWidth())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SheetAction("Import", "resonance.sheet.import", onImportRequested, Modifier.weight(1f))
                        SheetAction("Export", "resonance.sheet.export", onExportRequested, Modifier.weight(1f))
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SheetAction("+ New", "resonance.sheet.add", onAddTuning, Modifier.weight(1f))
                    SheetAction("Import", "resonance.sheet.import", onImportRequested, Modifier.weight(1f))
                    SheetAction("Export", "resonance.sheet.export", onExportRequested, Modifier.weight(1f))
                }
            }
            state.importResultText?.let { message ->
                Text(
                    text = message,
                    style = InstrumentType.readoutSmall,
                    color = p.brassLight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("resonance.sheet.importResult")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun RowEditDelete(
    row: PresetRowUi,
    onEditTuning: (String) -> Unit,
    onDeleteTuning: (String) -> Unit,
) {
    val p = InstrumentTheme.palette
    TextButton(
        onClick = { onEditTuning(row.id) },
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("resonance.sheet.row.${row.id}.edit")
            .semantics { contentDescription = "Edit ${row.name}" },
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = p.textMuted),
    ) { Text("Edit") }
    TextButton(
        onClick = { onDeleteTuning(row.id) },
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("resonance.sheet.row.${row.id}.delete")
            .semantics { contentDescription = "Delete ${row.name}" },
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = p.lampRed),
    ) { Text("Delete") }
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
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = InstrumentType.readoutSmall,
            modifier = Modifier
                .weight(1f)
                .testTag("resonance.micStatus"),
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

@Composable
private fun SheetAction(
    text: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    InstrumentKey(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(tag),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(text, style = InstrumentType.keyLabel, maxLines = 1, softWrap = false)
    }
}

private fun Double.toIntIfWholeOrOneDecimal(): String {
    val tenths = (this * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}