package dev.anima.resonance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.anima.resonance.engine.TunerController
import dev.anima.resonance.presentation.CustomEditorUi
import dev.anima.resonance.presentation.CustomEditorUiState
import dev.anima.resonance.presentation.EditorStringUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MainScreen: the host composable — a [TunerController] in, a
 * rendered instrument screen out.
 *
 * The ticker re-reads the immutable UI state (the engine is pull-based and
 * the read is cheap); mutations go through the controller and refresh
 * immediately so taps never wait for the next tick. The platform shell
 * (Android Activity) starts/stops capture via the controller lifecycle;
 * [onPermissionCta] is the hook that opens system app settings.
 *
 * The inline settings/preset sheet stays on the scrollable
 * instrument surface; a compact "Tunings" shortcut in the header brings it
 * into view. The legacy Settings/Tunings buttons remain available to
 * state-driven tests but are hidden here.
 */
@Composable
fun MainScreen(
    controller: TunerController,
    modifier: Modifier = Modifier,
    refreshMs: Long = 50,
    onPermissionCta: () -> Unit = {},
    /** Re-request a soft-denied microphone permission. */
    onRequestPermission: () -> Unit = {},
    /** Open the document picker for import (Android shell). */
    onImportPicker: () -> Unit = {},
    /** Open the document picker for export (Android shell). */
    onExportPicker: () -> Unit = {},
    /**
     * A tuning-file payload delivered by the shell (the Android
     * document picker reads the file and hands the text over). Null =
     * nothing pending. Handled once, then [onImportConsumed] fires.
     */
    importPayload: String? = null,
    onImportConsumed: () -> Unit = {},
    /**
     * Platform back/dismiss hook for the configuration overlay. The
     * Android shell wires androidx.activity.compose.BackHandler here; other
     * platforms default to a no-op (the overlay's Close key and scrim work
     * everywhere). Keeps platform concerns out of the common UI.
     */
    platformBack: @Composable (Boolean, () -> Unit) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    var ui by remember(controller) { mutableStateOf(controller.state()) }
    // Last import/export result message (UI-local; the ticker merges
    // it into the mapped state so it survives refreshes).
    var importMessage by remember(controller) { mutableStateOf<String?>(null) }

    LaunchedEffect(controller) {
        while (isActive) {
            ui = controller.state().copy(importResultText = importMessage)
            delay(refreshMs)
        }
    }

    fun refresh() {
        ui = controller.state().copy(importResultText = importMessage)
    }

    /**
     * Handles a tuning file payload (Android's document picker hands
     * it over). Result feedback renders in the sheet.
     */
    fun handleImportPayload(payload: String) {
        scope.launch {
            val result = controller.importCustomPresets(payload)
            importMessage = when {
                result.error != null -> "Import failed: ${result.error}"
                result.importedCount > 0 -> "Imported ${result.importedCount} tuning(s)"
                else -> "Import complete — no new tunings in file"
            }
            refresh()
        }
    }

    // The shell hands over a tuning-file payload (Android document
    // picker → text). Handle it exactly once, then signal consumption so
    // the shell can clear its state.
    LaunchedEffect(importPayload) {
        importPayload?.let { payload ->
            handleImportPayload(payload)
            onImportConsumed()
        }
    }

    // Custom-tuning editor form state (create: editingId = null).
    var editor by remember(controller) { mutableStateOf<CustomEditorUiState?>(null) }
    // A custom tuning is durable user data; deletion is confirmed, never a
    // single mis-tap on a row's trailing action.
    var pendingDelete by remember(controller) {
        mutableStateOf<dev.anima.resonance.presentation.PresetRowUi?>(null)
    }

    // Configuration lives in a modal overlay over the fixed instrument.
    var showSheet by remember(controller) { mutableStateOf(false) }

    fun openEditor(id: String? = null) {
        // Structured source of truth: the visible preview is a formatted
        // label. Parsing it dropped flat notes (the old regex accepted only
        // '#') and would rewrite imported tunings on save.
        val preset = id?.let { controller.preset(it) }
        // The row could have been deleted between render and tap; opening an
        // editor for a missing/built-in preset would fail on save.
        if (id != null && (preset == null || preset.builtIn)) return
        val strings = preset?.strings
            ?.map { CustomEditorUi.toEditorString(it.note) }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(EditorStringUi("E", 2))
        editor = CustomEditorUiState(
            name = preset?.name ?: "",
            strings = strings,
            editingId = id,
            isEdit = id != null,
        )
        showSheet = true
    }

    fun updateEditor(transform: (CustomEditorUiState) -> CustomEditorUiState) {
        editor = editor?.let {
            val next = transform(it)
            next.copy(errors = CustomEditorUi.validate(next.name, next.strings.map { s -> s.label }))
        }
    }

    fun saveEditor() {
        val form = editor ?: return
        // Re-validate on save: the form may be untouched since openEditor,
        // when the errors list was not (re)computed.
        val errors = CustomEditorUi.validate(form.name, form.strings.map { it.label })
        if (errors.isNotEmpty()) {
            editor = form.copy(errors = errors)
            return
        }
        val notes = CustomEditorUi.toNotes(form.strings.map { it.label })
        scope.launch {
            val target = form.editingId
            if (target != null) controller.editCustomPreset(target, form.name, notes)
            else controller.createCustomPreset(form.name, notes)
            editor = null
            refresh()
        }
    }

    TunerScreen(
        state = ui,
        onModeSelect = { mode ->
            if (mode != ui.mode) {
                scope.launch {
                    controller.setMode(mode)
                    refresh()
                }
            }
        },
        onStringSelect = { index ->
            controller.selectString(index)
            refresh()
        },
        onPermissionCta = onPermissionCta,
        onRequestPermission = onRequestPermission,
        onRetryCapture = {
            scope.launch {
                controller.retryCapture()
                refresh()
            }
        },
        onOpenSheet = { showSheet = true },
        modifier = modifier,
    )

    if (showSheet) {
        TuningsOverlay(
            state = ui,
            onClose = { showSheet = false },
            onPresetSelect = { id ->
                scope.launch {
                    controller.setPreset(id)
                    refresh()
                }
            },
            onToggleFavorite = { id ->
                scope.launch {
                    controller.toggleFavorite(id)
                    refresh()
                }
            },
            onConcertStep = { up ->
                scope.launch {
                    controller.stepConcertPitch(up)
                    refresh()
                }
            },
            onConcertStepCoarse = { up ->
                scope.launch {
                    controller.stepConcertPitch(up, stepHz = 1.0)
                    refresh()
                }
            },
            onAddTuning = { openEditor() },
            onEditTuning = { openEditor(it) },
            onDeleteTuning = { id ->
                pendingDelete = ui.presetRows.firstOrNull { it.id == id }
            },
            onSpellingSelect = { spelling ->
                scope.launch {
                    controller.setAccidentalSpelling(spelling)
                    refresh()
                }
            },
            onImportRequested = onImportPicker,
            onExportRequested = onExportPicker,
            editor = editor,
            onEditorNameChange = { name -> updateEditor { it.copy(name = name) } },
            onEditorNoteCycle = { index, up ->
                updateEditor { form ->
                    form.copy(strings = form.strings.mapIndexed { i, s ->
                        if (i == index) s.copy(noteName = CustomEditorUi.nextNoteName(s.noteName, up)) else s
                    })
                }
            },
            onEditorOctaveCycle = { index, up ->
                updateEditor { form ->
                    form.copy(strings = form.strings.mapIndexed { i, s ->
                        if (i == index) s.copy(octave = CustomEditorUi.nextOctave(s.octave, up)) else s
                    })
                }
            },
            onEditorRemoveString = { index ->
                updateEditor { form ->
                    form.copy(strings = form.strings.filterIndexed { i, _ -> i != index })
                }
            },
            onEditorAddString = {
                updateEditor { form ->
                    if (form.strings.size < CustomEditorUi.MAX_STRINGS) {
                        form.copy(strings = form.strings + EditorStringUi("E", 2))
                    } else form
                }
            },
            onEditorSave = { saveEditor() },
            onEditorCancel = { editor = null },
            platformBack = platformBack,
        )
    }

    pendingDelete?.let { target ->
        val p = dev.anima.resonance.ui.instrument.InstrumentTheme.palette
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            modifier = Modifier.testTag("resonance.deleteDialog"),
            containerColor = p.walnut,
            titleContentColor = p.ivory,
            textContentColor = p.textMuted,
            title = { Text("Delete tuning?") },
            text = {
                Text("Delete \"${target.name}\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            controller.deleteCustomPreset(target.id)
                            refresh()
                        }
                    },
                    modifier = Modifier.testTag("resonance.deleteDialog.confirm"),
                ) { Text("Delete", color = p.lampRed) }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    modifier = Modifier.testTag("resonance.deleteDialog.cancel"),
                ) { Text("Cancel", color = p.textMuted) }
            },
        )
    }
}
