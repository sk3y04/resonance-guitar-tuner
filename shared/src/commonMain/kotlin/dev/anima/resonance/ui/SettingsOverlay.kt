package dev.anima.resonance.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.backhandler.PredictiveBackHandler
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.music.Spelling
import dev.anima.resonance.presentation.CustomEditorUiState
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.NoteSequence
import dev.anima.resonance.presentation.PresetRowUi
import dev.anima.resonance.presentation.SheetSection
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.ui.instrument.CalibrationControls
import dev.anima.resonance.ui.instrument.FadeInOnChange
import dev.anima.resonance.ui.instrument.InstrumentSegment
import dev.anima.resonance.ui.instrument.InstrumentTheme
import dev.anima.resonance.ui.instrument.InstrumentTokens
import dev.anima.resonance.ui.instrument.InstrumentType
import dev.anima.resonance.ui.instrument.LocalReducedMotion
import dev.anima.resonance.ui.instrument.MotionTokens
import dev.anima.resonance.ui.instrument.PrimaryButton
import dev.anima.resonance.ui.instrument.RecessedPanel
import dev.anima.resonance.ui.instrument.SegmentTrack
import dev.anima.resonance.ui.instrument.SpellingKeys
import dev.anima.resonance.ui.instrument.TextAction
import dev.anima.resonance.ui.instrument.TonalButton
import kotlinx.coroutines.CancellationException

/**
 * The settings surface — three mutually exclusive DESTINATIONS.
 *
 * Not one long page with tab-looking navigation: only the selected destination
 * is composed (tuning rows cannot remain above calibration), each destination
 * keeps its own scroll position, and the shell (title, close, destination nav,
 * one viewport) stays put. Entry points from the instrument land on the
 * matching destination.
 *
 * Motion & system integration:
 *  - the sheet slides up from the bottom edge while the scrim fades in on the
 *    SAME animation value (no background flash, no content before container);
 *    the host wraps the overlay in a matching fade for a coordinated exit;
 *  - system Back closes the sheet — or cancels the editor first when one is
 *    open — and uses the predictive-back gesture where the platform supports
 *    it (the sheet tracks the gesture progress and springs back on cancel);
 *  - safe-drawing insets (including the IME) are applied OUTSIDE the size
 *    measurement, so the keyboard can never push the sheet off-screen;
 *  - destination changes fade the content only; the shell never moves.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TuningsOverlay(
    state: MainScreenUiState,
    onClose: () -> Unit,
    onConcertStep: (Boolean) -> Unit = {},
    onConcertStepCoarse: (Boolean) -> Unit = {},
    onConcertSet: (Double) -> Unit = {},
    onSpellingSelect: (Spelling) -> Unit = {},
    onPresetSelect: (String) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onAddTuning: () -> Unit = {},
    onEditTuning: (String) -> Unit = {},
    onDeleteTuning: (String) -> Unit = {},
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
    platformBack: @Composable (Boolean, () -> Unit) -> Unit = { _, _ -> },
    /** Which destination an entry point opens. Null = Tuning. */
    initialSection: SheetSection? = null,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    val reduced = LocalReducedMotion.current

    // ── System Back: editor first, then the sheet ────────────────────────────
    // Where a back dispatcher exists (Android, and hosts that provide one) the
    // common handlers register directly and the sheet tracks predictive-back
    // progress. Otherwise fall back to the platform hook (legacy shell wiring).
    var backProgress by remember { mutableStateOf(0f) }
    val dispatcherOwner = LocalNavigationEventDispatcherOwner.current
    if (dispatcherOwner != null) {
        BackHandler(enabled = editor != null) { onEditorCancel() }
        PredictiveBackHandler(enabled = editor == null) { progress ->
            try {
                progress.collect { event -> backProgress = event.progress }
                // Gesture committed.
                backProgress = 0f
                onClose()
            } catch (e: CancellationException) {
                // Gesture cancelled — the sheet returns to rest.
                backProgress = 0f
            }
        }
    } else {
        platformBack(true) { if (editor != null) onEditorCancel() else onClose() }
    }

    var destination by remember { mutableStateOf(initialSection ?: SheetSection.TUNING) }
    // One scroll position per destination, so switching never loses a place.
    val tuningScroll = rememberScrollState()
    val calibrationScroll = rememberScrollState()
    val manageScroll = rememberScrollState()
    val activeScroll = when (destination) {
        SheetSection.TUNING -> tuningScroll
        SheetSection.CALIBRATION -> calibrationScroll
        SheetSection.MANAGE -> manageScroll
    }

    // ── Entrance: ONE value drives sheet offset AND scrim alpha ─────────────
    // 1 = sheet fully below its slot, scrim transparent; 0 = at rest. Reading
    // the value only inside draw/layer blocks keeps the animation off the
    // recomposition path. Reduced motion starts (and stays) at rest.
    val slide = remember { Animatable(if (reduced) 0f else 1f) }
    LaunchedEffect(Unit) { slide.animateTo(0f, MotionTokens.sheet(reduced)) }

    // Insets (status/nav bars, cutout AND the IME) are applied before measuring,
    // so the 92% sheet height is computed from the space actually available.
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sheetHeight = maxHeight * 0.92f
            val sheetHeightPx = with(LocalDensity.current) { sheetHeight.toPx() }
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val alpha = 0.55f * (1f - slide.value) * (1f - backProgress)
                        drawRect(Color.Black.copy(alpha = alpha))
                    }
                    .clickable(onClickLabel = "Close settings", onClick = onClose),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                RecessedPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sheetHeight)
                        // The sheet SURFACE consumes touches that no child
                        // handled. Without this, taps/swipes on empty sheet area
                        // (below short content, the header, padding) fell through
                        // to the scrim behind and dismissed the sheet — verified
                        // on the emulator. Children (rows, keys, the scroll
                        // viewport) consume their own gestures first, so this
                        // never competes with them; it adds no semantics.
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = true).consume()
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .graphicsLayer {
                            translationY =
                                slide.value * sheetHeightPx + backProgress * sheetHeightPx * 0.3f
                            alpha = 1f - backProgress * 0.25f
                        }
                        .border(
                            width = 1.dp,
                            color = p.deckEdge.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(InstrumentTokens.cornerSheet),
                        )
                        .testTag("resonance.overlay"),
                    shape = RoundedCornerShape(InstrumentTokens.cornerSheet),
                    // Same material family as the deck (gentle top light), but
                    // the modal stays quieter: no grain, no seam shadow.
                    faceplate = true,
                ) {
                    Column {
                        // ── Stable shell: title + close ──────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = InstrumentTokens.gutter,
                                    end = InstrumentTokens.space2,
                                    top = InstrumentTokens.space4,
                                    bottom = InstrumentTokens.space3,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (editor != null) {
                                    if (editor.isEdit) "Edit tuning" else "New tuning"
                                } else {
                                    "Settings"
                                },
                                style = InstrumentType.display,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = InstrumentTokens.space3)
                                    .semantics { heading() }
                                    .testTag("resonance.sheet.title"),
                            )
                            // Content-sized header action, never the sheet's primary
                            // button. The title above holds the flexible width, so the
                            // two sit at opposite ends of one compact row.
                            TonalButton(
                                text = "Done",
                                onClick = onClose,
                                modifier = Modifier
                                    .widthIn(min = 76.dp)
                                    .testTag("resonance.overlay.close")
                                    .semantics { contentDescription = "Close settings" },
                            )
                        }
                        if (editor == null) {
                            SegmentTrack(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = InstrumentTokens.gutter),
                            ) {
                                InstrumentSegment(
                                    label = "Tuning",
                                    selected = destination == SheetSection.TUNING,
                                    onClick = { destination = SheetSection.TUNING },
                                    tag = "resonance.sheet.dest.tuning",
                                    description = "Tuning destination",
                                )
                                InstrumentSegment(
                                    label = "Calibration",
                                    selected = destination == SheetSection.CALIBRATION,
                                    onClick = { destination = SheetSection.CALIBRATION },
                                    tag = "resonance.sheet.dest.calibration",
                                    description = "Calibration and note names destination",
                                )
                                InstrumentSegment(
                                    label = "Manage",
                                    selected = destination == SheetSection.MANAGE,
                                    onClick = { destination = SheetSection.MANAGE },
                                    tag = "resonance.sheet.dest.manage",
                                    description = "Custom tunings destination",
                                )
                            }
                            Spacer(Modifier.height(InstrumentTokens.space3))
                        }
                        // ── ONE viewport for the active destination ──────────
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(activeScroll)
                                .testTag("resonance.overlay.scroll")
                                .padding(horizontal = InstrumentTokens.gutter)
                                .padding(bottom = InstrumentTokens.space5),
                        ) {
                            // Content-only transition: the shell above never
                            // moves, and only the active destination is composed.
                            FadeInOnChange(
                                key = (editor != null) to destination,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (editor != null) {
                                    CustomEditorSheet(
                                        state = editor,
                                        onNameChange = onEditorNameChange,
                                        onNoteCycle = onEditorNoteCycle,
                                        onOctaveCycle = onEditorOctaveCycle,
                                        onRemoveString = onEditorRemoveString,
                                        onAddString = onEditorAddString,
                                        onSave = onEditorSave,
                                        onCancel = onEditorCancel,
                                    )
                                } else {
                                    when (destination) {
                                        SheetSection.TUNING -> TuningDestination(
                                            state = state,
                                            onPresetSelect = onPresetSelect,
                                            onToggleFavorite = onToggleFavorite,
                                        )
                                        SheetSection.CALIBRATION -> CalibrationDestination(
                                            state = state,
                                            onConcertStep = onConcertStep,
                                            onConcertStepCoarse = onConcertStepCoarse,
                                            onConcertSet = onConcertSet,
                                            onSpellingSelect = onSpellingSelect,
                                        )
                                        SheetSection.MANAGE -> ManageDestination(
                                            state = state,
                                            onAddTuning = onAddTuning,
                                            onEditTuning = onEditTuning,
                                            onDeleteTuning = onDeleteTuning,
                                            onImportRequested = onImportRequested,
                                            onExportRequested = onExportRequested,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Destination 1 — choose the tuning. Only tuning content is composed here. */
@Composable
private fun TuningDestination(
    state: MainScreenUiState,
    onPresetSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("resonance.sheet"),
    ) {
        if (state.mode != TunerMode.INSTRUMENT) {
            Text(
                text = "Used by Instrument mode.",
                style = InstrumentType.body,
                modifier = Modifier.padding(bottom = InstrumentTokens.space3),
            )
        }
        if (state.presetRows.isEmpty()) {
            Text("No tunings available yet.", style = InstrumentType.body)
        } else {
            // Stable keys: selecting or favouriting one row never replays an
            // entrance animation for the whole list.
            Column(verticalArrangement = Arrangement.spacedBy(InstrumentTokens.space1)) {
                state.presetRows.forEach { row ->
                    androidx.compose.runtime.key(row.id) {
                        TuningRow(row, onPresetSelect, onToggleFavorite)
                    }
                }
            }
        }
    }
}

/** Destination 2 — reference pitch and note names, nothing else. */
@Composable
private fun CalibrationDestination(
    state: MainScreenUiState,
    onConcertStep: (Boolean) -> Unit,
    onConcertStepCoarse: (Boolean) -> Unit,
    onConcertSet: (Double) -> Unit,
    onSpellingSelect: (Spelling) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("resonance.sheet"),
    ) {
        CalibrationControls(
            concertHz = state.concertPitchHz,
            concertError = state.concertError,
            onConcertStep = onConcertStep,
            onConcertStepCoarse = onConcertStepCoarse,
            onConcertSet = onConcertSet,
            largeText = false,
        )
        Spacer(Modifier.height(InstrumentTokens.sectionGap))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("resonance.sheet.spelling"),
        ) {
            Text("Note names", style = InstrumentType.label)
            Spacer(Modifier.height(InstrumentTokens.space2))
            SpellingKeys(state.accidentalSpelling, onSpellingSelect, Modifier.fillMaxWidth())
        }
    }
}

/** Destination 3 — custom tuning management and data actions. */
@Composable
private fun ManageDestination(
    state: MainScreenUiState,
    onAddTuning: () -> Unit,
    onEditTuning: (String) -> Unit,
    onDeleteTuning: (String) -> Unit,
    onImportRequested: () -> Unit,
    onExportRequested: () -> Unit,
) {
    val p = InstrumentTheme.palette
    val customs = state.presetRows.filter { !it.builtIn }
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("resonance.sheet"),
    ) {
        PrimaryButton(
            text = "New tuning",
            onClick = onAddTuning,
            modifier = Modifier.fillMaxWidth(),
            tag = "resonance.sheet.add",
        )
        Spacer(Modifier.height(InstrumentTokens.space2))
        Row(horizontalArrangement = Arrangement.spacedBy(InstrumentTokens.space2)) {
            TonalButton(
                text = "Import",
                onClick = onImportRequested,
                modifier = Modifier.weight(1f),
                tag = "resonance.sheet.import",
            )
            TonalButton(
                text = "Export",
                onClick = onExportRequested,
                modifier = Modifier.weight(1f),
                tag = "resonance.sheet.export",
            )
        }
        Spacer(Modifier.height(InstrumentTokens.sectionGap))
        Text("Custom tunings", style = InstrumentType.label)
        Spacer(Modifier.height(InstrumentTokens.space2))
        if (customs.isEmpty()) {
            Text(
                text = "No custom tunings yet. Create one to add a tuning of your own.",
                style = InstrumentType.body,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(InstrumentTokens.space1)) {
                customs.forEach { row ->
                    androidx.compose.runtime.key(row.id) {
                        ManageRow(row, onEditTuning, onDeleteTuning)
                    }
                }
            }
        }
        state.importResultText?.let { message ->
            Spacer(Modifier.height(InstrumentTokens.space3))
            Text(
                text = message,
                style = InstrumentType.body.copy(color = p.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resonance.sheet.importResult")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * One tuning row: name, string sequence, a single checkmark when current, and a
 * separately targetable favourite key. The selected wash and the star settle
 * with the shared selection tween; the check appears immediately so selection
 * feedback is never delayed by decoration.
 */
@Composable
private fun TuningRow(
    row: PresetRowUi,
    onPresetSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    val p = InstrumentTheme.palette
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val rowFill by animateColorAsState(
        targetValue = if (row.selected) p.accent.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = MotionTokens.selection(reduced),
        label = "tuning-row-fill",
    )
    Surface(
        onClick = {
            // One subtle haptic for a deliberate tuning choice (system-gated).
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onPresetSelect(row.id)
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("resonance.sheet.row.${row.id}")
            .semantics {
                contentDescription = "Tuning ${row.name}" +
                    (if (row.selected) ", selected" else "") +
                    (if (row.favorite) ", favorite" else "")
            },
        shape = RoundedCornerShape(InstrumentTokens.cornerMd),
        color = rowFill,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = InstrumentTokens.space3,
                    end = InstrumentTokens.space1,
                    top = InstrumentTokens.space2,
                    bottom = InstrumentTokens.space2,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = InstrumentType.label.copy(
                        fontWeight = if (row.selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                NoteSequenceText(row.stringsPreview)
            }
            if (row.selected) {
                Text(
                    text = "✓",
                    style = InstrumentType.label.copy(color = p.accent, fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .padding(horizontal = InstrumentTokens.space2)
                        .clearAndSetSemantics {},
                )
            }
            FavoriteKey(row, onToggleFavorite)
        }
    }
}

/** A custom tuning inside the Manage destination: management, not selection. */
@Composable
private fun ManageRow(
    row: PresetRowUi,
    onEditTuning: (String) -> Unit,
    onDeleteTuning: (String) -> Unit,
) {
    val p = InstrumentTheme.palette
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("resonance.sheet.row.${row.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = InstrumentTokens.space3, top = InstrumentTokens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.name, style = InstrumentType.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                NoteSequenceText(row.stringsPreview)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextAction(
                text = "Edit",
                onClick = { onEditTuning(row.id) },
                tag = "resonance.sheet.row.${row.id}.edit",
                modifier = Modifier.semantics { contentDescription = "Edit ${row.name}" },
            )
            TextAction(
                text = "Delete",
                onClick = { onDeleteTuning(row.id) },
                tag = "resonance.sheet.row.${row.id}.delete",
                modifier = Modifier.semantics { contentDescription = "Delete ${row.name}" },
                color = p.terracotta,
            )
        }
    }
}

@Composable
private fun FavoriteKey(row: PresetRowUi, onToggleFavorite: (String) -> Unit) {
    val p = InstrumentTheme.palette
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val starColor by animateColorAsState(
        targetValue = if (row.favorite) p.accent else p.textSecondary,
        animationSpec = MotionTokens.selection(reduced),
        label = "favorite-star",
    )
    TextButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggleFavorite(row.id)
        },
        modifier = Modifier
            .size(InstrumentTokens.touchMin)
            .testTag("resonance.sheet.row.${row.id}.favorite")
            .semantics {
                contentDescription = if (row.favorite) {
                    "Remove ${row.name} from favorites"
                } else {
                    "Add ${row.name} to favorites"
                }
            },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = if (row.favorite) "★" else "☆",
            fontSize = 20.sp,
            color = starColor,
        )
    }
}

/** String sequence: note letters in body type, octaves as small secondary marks. */
@Composable
private fun NoteSequenceText(preview: String) {
    val p = InstrumentTheme.palette
    val tokens = NoteSequence.parse(preview)
    if (tokens.isEmpty()) return
    val text = buildAnnotatedString {
        tokens.forEachIndexed { index, token ->
            if (index > 0) append("  ")
            withStyle(SpanStyle(fontSize = 14.sp, color = p.textSecondary)) { append(token.label) }
            token.octave?.let { octave ->
                withStyle(
                    SpanStyle(
                        fontSize = 10.sp,
                        color = p.textSecondary.copy(alpha = 0.8f),
                        baselineShift = BaselineShift(-0.42f),
                    ),
                ) { append(octave) }
            }
        }
    }
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
