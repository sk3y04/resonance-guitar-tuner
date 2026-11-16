package dev.anima.resonance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.music.Spelling
import dev.anima.resonance.presentation.CustomEditorUiState
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.PresetRowUi
import dev.anima.resonance.presentation.PresetSheetUi
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.ui.instrument.CalibrationControls
import dev.anima.resonance.ui.instrument.InstrumentKey
import dev.anima.resonance.ui.instrument.InstrumentTheme
import dev.anima.resonance.ui.instrument.InstrumentType
import dev.anima.resonance.ui.instrument.RecessedPanel
import dev.anima.resonance.ui.instrument.SpellingKeys

/**
 * The configuration overlay — a configuration panel belonging to the
 * wooden instrument, not a full-screen settings page. Three regions: a compact
 * header (title + Close), scrolling content grouped into CALIBRATION and
 * TUNINGS sections on one shared dark panel, and a compact action footer
 * (New / Import / Export). A restrained brass rim ties it to the instrument;
 * the dimmed instrument stays visible behind it. Dismiss via Close, the scrim,
 * or platform back. All tags/semantics of the former sheet are preserved.
 */
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
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    platformBack(true, onClose)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * 0.92f
        // Scrim: the instrument remains visible behind the overlay.
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClickLabel = "Close settings", onClick = onClose),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(10.dp),
        ) {
            RecessedPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .border(1.dp, p.brassDark.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                    .testTag("resonance.overlay"),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column {
                    // ── Header (fixed) ───────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (editor != null) {
                                if (editor.isEdit) "Edit tuning" else "New tuning"
                            } else {
                                "Tunings & settings"
                            },
                            style = InstrumentType.engravedStrong.copy(fontSize = 16.sp),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { heading() }
                                .testTag("resonance.sheet.title"),
                        )
                        InstrumentKey(
                            onClick = onClose,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("resonance.overlay.close")
                                .semantics { contentDescription = "Close tunings and settings" },
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        ) { Text("Done", style = InstrumentType.keyLabel) }
                    }
                    HairlineDivider()
                    // ── Content (scrolls) ────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .testTag("resonance.overlay.scroll")
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
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
                            SheetBody(
                                state = state,
                                onConcertStep = onConcertStep,
                                onConcertStepCoarse = onConcertStepCoarse,
                                onConcertSet = onConcertSet,
                                onSpellingSelect = onSpellingSelect,
                                onPresetSelect = onPresetSelect,
                                onToggleFavorite = onToggleFavorite,
                                onEditTuning = onEditTuning,
                                onDeleteTuning = onDeleteTuning,
                            )
                        }
                    }
                    // ── Footer (fixed, secondary actions) ────────────────
                    if (editor == null) {
                        HairlineDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // "New tuning" stays discoverable and prominent;
                            // Import/Export move into a clearly labelled secondary
                            // "More" management area (not three equal buttons).
                            FooterAction("+ New tuning", "resonance.sheet.add", onAddTuning, Modifier.fillMaxWidth())
                            Text(
                                text = "MORE",
                                style = InstrumentType.engraved.copy(
                                    color = p.brassLight,
                                    fontSize = 10.sp,
                                    letterSpacing = 2.sp,
                                ),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FooterAction("Import", "resonance.sheet.import", onImportRequested, Modifier.weight(1f))
                                FooterAction("Export", "resonance.sheet.export", onExportRequested, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The configuration body. Instrument-mode order: presets, reference pitch,
 *  note spelling, then secondary management. Chromatic: the relevant settings
 *  (reference + spelling) first, with the instrument-preset list clearly labelled
 *  as applying to Instrument mode. One shared surface. */
@Composable
private fun SheetBody(
    state: MainScreenUiState,
    onConcertStep: (Boolean) -> Unit,
    onConcertStepCoarse: (Boolean) -> Unit,
    onConcertSet: (Double) -> Unit,
    onSpellingSelect: (Spelling) -> Unit,
    onPresetSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEditTuning: (String) -> Unit,
    onDeleteTuning: (String) -> Unit,
) {
    val p = InstrumentTheme.palette
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    val instrument = state.mode == TunerMode.INSTRUMENT

    @Composable
    fun CalibrationSection() {
        SectionLabel("REFERENCE PITCH")
        Spacer(Modifier.size(8.dp))
        CalibrationControls(
            concertHz = state.concertPitchHz,
            concertError = state.concertError,
            onConcertStep = onConcertStep,
            onConcertStepCoarse = onConcertStepCoarse,
            onConcertSet = onConcertSet,
            largeText = largeText,
        )
        Spacer(Modifier.size(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("resonance.sheet.spelling"),
        ) {
            Text("Note spelling", style = InstrumentType.engraved)
            Spacer(Modifier.size(6.dp))
            SpellingKeys(state.accidentalSpelling, onSpellingSelect, Modifier.fillMaxWidth())
        }
    }

    @Composable
    fun TuningsSection() {
        SectionLabel(if (instrument) "TUNINGS" else "INSTRUMENT TUNINGS")
        if (!instrument) {
            Text(
                text = "Applies to Instrument mode",
                style = InstrumentType.readoutSmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
        }
        Spacer(Modifier.size(8.dp))
        state.presetRows.forEach { row ->
            TuningRow(row, onPresetSelect, onToggleFavorite, onEditTuning, onDeleteTuning)
        }
    }

    Column(Modifier.testTag("resonance.sheet")) {
        if (instrument) {
            TuningsSection()
            Spacer(Modifier.size(16.dp))
            HairlineDivider()
            Spacer(Modifier.size(16.dp))
            CalibrationSection()
        } else {
            CalibrationSection()
            Spacer(Modifier.size(16.dp))
            HairlineDivider()
            Spacer(Modifier.size(16.dp))
            TuningsSection()
        }
        state.importResultText?.let { message ->
            Text(
                text = message,
                style = InstrumentType.readoutSmall,
                color = p.brassLight,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .testTag("resonance.sheet.importResult")
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** One tuning: name + ordered string notes, active highlight, separate favorite. */
@Composable
private fun TuningRow(
    row: PresetRowUi,
    onPresetSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEditTuning: (String) -> Unit,
    onDeleteTuning: (String) -> Unit,
) {
    val p = InstrumentTheme.palette
    Surface(
        onClick = { onPresetSelect(row.id) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .testTag("resonance.sheet.row.${row.id}")
            .semantics {
                contentDescription = "Tuning ${row.name}" +
                    (if (row.selected) ", selected" else "") +
                    (if (row.favorite) ", favorite" else "")
            },
        shape = RoundedCornerShape(8.dp),
        color = if (row.selected) p.walnutLight.copy(alpha = 0.45f) else p.recess,
        border = if (row.selected) BorderStroke(1.dp, p.brass.copy(alpha = 0.9f)) else null,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name,
                    style = InstrumentType.keyLabel.copy(color = p.ivory, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (row.selected) {
                    Text(
                        text = "✓",
                        color = p.brassLight,
                        style = InstrumentType.readoutSmall.copy(fontWeight = FontWeight.Bold),
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
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (row.favorite) p.brassLight else p.textMuted,
                    ),
                ) {
                    Text(text = if (row.favorite) "★" else "☆", fontSize = 20.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.stringsPreview,
                    style = InstrumentType.readoutSmall.copy(
                        fontSize = 15.sp,
                        color = p.ivory.copy(alpha = 0.82f),
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (!row.builtIn) {
                    RowEditDelete(row, onEditTuning, onDeleteTuning)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val p = InstrumentTheme.palette
    Text(
        text = text,
        style = InstrumentType.engraved.copy(
            color = p.brassLight,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        ),
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun HairlineDivider() {
    val p = InstrumentTheme.palette
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(p.brassDark.copy(alpha = 0.45f)),
    )
}

/** A secondary, outlined footer action (visually lighter than the tuning list). */
@Composable
private fun FooterAction(
    text: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val p = InstrumentTheme.palette
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(tag),
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        contentColor = p.ivory,
        border = BorderStroke(1.dp, p.brassDark.copy(alpha = 0.9f)),
    ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, style = InstrumentType.keyLabel, maxLines = 1, softWrap = false)
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