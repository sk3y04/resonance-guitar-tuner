package dev.anima.resonance.ui

import androidx.compose.foundation.background
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
import dev.anima.resonance.ui.instrument.CalibrationControls
import dev.anima.resonance.ui.instrument.InstrumentKey
import dev.anima.resonance.ui.instrument.InstrumentTheme
import dev.anima.resonance.ui.instrument.InstrumentType
import dev.anima.resonance.ui.instrument.RecessedPanel
import dev.anima.resonance.ui.instrument.SpellingKeys

/**
 * The configuration overlay — one coherent modal surface over the
 * instrument (which stays in place behind it). Carries the full existing
 * configuration contract: tuning selection + favorites, A4 reference with
 * coarse/fine steps, accidentals, new/import/export, and the custom-tuning
 * editor. The overlay scrolls; the main instrument does not. Dismiss via
 * the Close key, the scrim, or platform back (wired by the shell through
 * [platformBack]). All tags/semantics of the former inline sheet are kept.
 */
@Composable
fun TuningsOverlay(
    state: MainScreenUiState,
    onClose: () -> Unit,
    onConcertStep: (Boolean) -> Unit = {},
    onConcertStepCoarse: (Boolean) -> Unit = {},
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
                    .testTag("resonance.overlay"),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (editor != null) {
                                if (editor.isEdit) "Edit tuning" else "New tuning"
                            } else {
                                "Tunings & settings"
                            },
                            style = InstrumentType.engravedStrong,
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
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text("Close", style = InstrumentType.keyLabel) }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .testTag("resonance.overlay.scroll")
                            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
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
                                largeText = largeText,
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
                    }
                }
            }
        }
    }
}

/** The configuration body (former inline sheet), unchanged contract. */
@Composable
private fun SheetBody(
    state: MainScreenUiState,
    largeText: Boolean,
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
    Column(Modifier.testTag("resonance.sheet")) {
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
                ) { SpellingKeys(state.accidentalSpelling, onSpellingSelect) }
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
                shape = RoundedCornerShape(8.dp),
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
                            Text(text = if (row.favorite) "★" else "☆", fontSize = 18.sp)
                        }
                    }
                    if (largeText) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(row.stringsPreview, style = InstrumentType.readoutSmall)
                            if (!row.builtIn) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) { RowEditDelete(row, onEditTuning, onDeleteTuning) }
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