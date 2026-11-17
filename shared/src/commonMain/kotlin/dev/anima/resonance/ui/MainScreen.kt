package dev.anima.resonance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.anima.resonance.engine.TunerController
import dev.anima.resonance.presentation.CustomEditorUi
import dev.anima.resonance.presentation.CustomEditorUiState
import dev.anima.resonance.presentation.EditorStringUi
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.SignalHold
import dev.anima.resonance.presentation.SignalPhase
import dev.anima.resonance.presentation.TunerStatus
import dev.anima.resonance.ui.instrument.LocalReducedMotion
import dev.anima.resonance.ui.instrument.MotionTokens
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
 * Performance & motion:
 *  - every handler is remembered against the controller, so the 20 Hz ticker
 *    does not invalidate the whole tree with fresh lambda identities;
 *  - the settings sheet receives a measurement-normalized state slice
 *    (derivedStateOf): pitch ticks never recompose the overlay;
 *  - the overlay enters/exits through one AnimatedVisibility fade coordinated
 *    with the sheet's internal slide + scrim — Back, scrim tap and Done all
 *    close through the same path, and re-opening mid-exit reverses cleanly.
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
     * Platform back/dismiss hook for the configuration overlay. Used as
     * the fallback where no back dispatcher owner exists; on Android the
     * overlay registers common (predictive) back handlers directly.
     */
    platformBack: @Composable (Boolean, () -> Unit) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    var importMessage by remember(controller) { mutableStateOf<String?>(null) }
    // Bounded hold of the last reliable reading across brief interruptions.
    // A virtual tick clock (tick × refreshMs) drives the named, testable window.
    var lastReliable by remember(controller) { mutableStateOf<MainScreenUiState?>(null) }
    var tick by remember(controller) { mutableStateOf(0L) }
    var lastReliableTick by remember(controller) { mutableStateOf(0L) }

    // Remembered once: the closure reads the mutable-state delegates above, so
    // it always sees current values without being recreated every tick.
    val computeUi: () -> MainScreenUiState = remember(controller) {
        {
            val base = controller.state().copy(importResultText = importMessage)
            tick++
            if (base.meterActive) {
                lastReliable = base
                lastReliableTick = tick
                base
            } else {
                val elapsedMs = (tick - lastReliableTick) * refreshMs
                SignalHold.apply(base, lastReliable, elapsedMs)
            }
        }
    }

    var ui by remember(controller) { mutableStateOf(computeUi()) }
    val refresh: () -> Unit = remember(controller) { { ui = computeUi() } }

    LaunchedEffect(controller) {
        while (isActive) {
            ui = computeUi()
            delay(refreshMs)
        }
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
    // picker → text). Handle it exactly once, then signal consumption so the
    // shell can clear its state.
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
    // One overlay, three entry points — remember which section to open on.
    var sheetSection by remember(controller) {
        mutableStateOf(dev.anima.resonance.presentation.SheetSection.TUNING)
    }

    val openEditor: (String?) -> Unit = remember(controller) {
        { id ->
            // Structured source of truth: the visible preview is a formatted
            // label. Parsing it dropped flat notes (the old regex accepted only
            // '#') and would rewrite imported tunings on save.
            val preset = id?.let { controller.preset(it) }
            // The row could have been deleted between render and tap; opening an
            // editor for a missing/built-in preset would fail on save.
            if (id == null || (preset != null && !preset.builtIn)) {
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
        }
    }

    val updateEditor: ((CustomEditorUiState) -> CustomEditorUiState) -> Unit = remember(controller) {
        { transform ->
            editor = editor?.let {
                val next = transform(it)
                next.copy(errors = CustomEditorUi.validate(next.name, next.strings.map { s -> s.label }))
            }
        }
    }

    val saveEditor: () -> Unit = remember(controller) {
        {
            val form = editor
            if (form != null) {
                // Re-validate on save: the form may be untouched since openEditor,
                // when the errors list was not (re)computed.
                val errors = CustomEditorUi.validate(form.name, form.strings.map { it.label })
                if (errors.isNotEmpty()) {
                    editor = form.copy(errors = errors)
                } else {
                    val notes = CustomEditorUi.toNotes(form.strings.map { it.label })
                    scope.launch {
                        val target = form.editingId
                        if (target != null) controller.editCustomPreset(target, form.name, notes)
                        else controller.createCustomPreset(form.name, notes)
                        editor = null
                        refresh()
                    }
                }
            }
        }
    }

    // ── Stable instrument handlers (no per-tick lambda churn) ────────────────
    val onModeSelect: (dev.anima.resonance.presentation.TunerMode) -> Unit = remember(controller) {
        { mode ->
            if (mode != controller.state().mode) {
                scope.launch {
                    controller.setMode(mode)
                    refresh()
                }
            }
        }
    }
    val onStringSelect: (Int) -> Unit = remember(controller) {
        { index ->
            controller.selectString(index)
            refresh()
        }
    }
    val onRetryCapture: () -> Unit = remember(controller) {
        {
            scope.launch {
                controller.retryCapture()
                refresh()
            }
        }
    }
    val onOpenSheet: () -> Unit = remember(controller) {
        {
            // The tuning row opens the preset list in Instrument mode and the
            // general settings in Chromatic mode (no instrument preset in use).
            sheetSection = dev.anima.resonance.presentation.SheetFocus
                .forInstrumentPresetEntry(controller.state().mode)
            showSheet = true
        }
    }
    val onOpenSettings: () -> Unit = remember(controller) {
        {
            sheetSection = dev.anima.resonance.presentation.SheetFocus.forSettings()
            showSheet = true
        }
    }
    val onAutoSelectionChange: (Boolean) -> Unit = remember(controller) {
        { auto ->
            // Map the visible On/Off toggle onto the engine's existing
            // lock/unlock semantics (unchanged controller API).
            val current = controller.state()
            val intent = dev.anima.resonance.presentation.StringSelectionUi.intent(
                currentlyAuto = current.selectedStringIndex == null,
                lockedIndex = current.selectedStringIndex,
                activeIndex = current.activeStringIndex,
                stringCount = current.targetLabels.size,
            )
            if (intent != null && auto != (current.selectedStringIndex == null)) {
                controller.selectString(intent)
                refresh()
            }
        }
    }

    TunerScreen(
        state = ui,
        onModeSelect = onModeSelect,
        onStringSelect = onStringSelect,
        onPermissionCta = onPermissionCta,
        onRequestPermission = onRequestPermission,
        onRetryCapture = onRetryCapture,
        onOpenSheet = onOpenSheet,
        onOpenSettings = onOpenSettings,
        onAutoSelectionChange = onAutoSelectionChange,
        modifier = modifier,
    )

    // ── Settings sheet state slice ───────────────────────────────────────────
    // The overlay never renders measurement fields; normalizing them here means
    // derivedStateOf emits a new value ONLY when a settings-relevant field
    // changes, so pitch ticks do not recompose the sheet.
    val sheetState by remember(controller) {
        derivedStateOf {
            ui.copy(
                status = TunerStatus.LISTENING,
                micStatusText = "",
                meterPosition = 0.0,
                meterClamped = false,
                meterActive = false,
                noteLabel = null,
                octave = null,
                frequencyText = null,
                centsText = null,
                directionText = null,
                statusHint = null,
                inTune = false,
                signalPhase = SignalPhase.WAITING,
                readingAlpha = 1f,
            )
        }
    }

    // ── Stable sheet handlers ────────────────────────────────────────────────
    val onPresetSelect: (String) -> Unit = remember(controller) {
        { id ->
            scope.launch {
                controller.setPreset(id)
                refresh()
            }
        }
    }
    val onToggleFavorite: (String) -> Unit = remember(controller) {
        { id ->
            scope.launch {
                controller.toggleFavorite(id)
                refresh()
            }
        }
    }
    val onConcertStep: (Boolean) -> Unit = remember(controller) {
        { up ->
            scope.launch {
                controller.stepConcertPitch(up)
                refresh()
            }
        }
    }
    val onConcertStepCoarse: (Boolean) -> Unit = remember(controller) {
        { up ->
            scope.launch {
                controller.stepConcertPitch(up, stepHz = 1.0)
                refresh()
            }
        }
    }
    val onConcertSet: (Double) -> Unit = remember(controller) {
        { hz ->
            scope.launch {
                controller.setConcertPitch(hz)
                refresh()
            }
        }
    }
    val onSpellingSelect: (dev.anima.resonance.music.Spelling) -> Unit = remember(controller) {
        { spelling ->
            scope.launch {
                controller.setAccidentalSpelling(spelling)
                refresh()
            }
        }
    }
    val onDeleteTuning: (String) -> Unit = remember(controller) {
        { id ->
            pendingDelete = controller.state().presetRows.firstOrNull { it.id == id }
        }
    }
    val onEditorNameChange: (String) -> Unit = remember(controller) {
        { name -> updateEditor { it.copy(name = name) } }
    }
    val onEditorNoteCycle: (Int, Boolean) -> Unit = remember(controller) {
        { index, up ->
            updateEditor { form ->
                form.copy(strings = form.strings.mapIndexed { i, s ->
                    if (i == index) s.copy(noteName = CustomEditorUi.nextNoteName(s.noteName, up)) else s
                })
            }
        }
    }
    val onEditorOctaveCycle: (Int, Boolean) -> Unit = remember(controller) {
        { index, up ->
            updateEditor { form ->
                form.copy(strings = form.strings.mapIndexed { i, s ->
                    if (i == index) s.copy(octave = CustomEditorUi.nextOctave(s.octave, up)) else s
                })
            }
        }
    }
    val onEditorRemoveString: (Int) -> Unit = remember(controller) {
        { index ->
            updateEditor { form ->
                form.copy(strings = form.strings.filterIndexed { i, _ -> i != index })
            }
        }
    }
    val onEditorAddString: () -> Unit = remember(controller) {
        {
            updateEditor { form ->
                if (form.strings.size < CustomEditorUi.MAX_STRINGS) {
                    form.copy(strings = form.strings + EditorStringUi("E", 2))
                } else form
            }
        }
    }
    val onEditorCancel: () -> Unit = remember(controller) { { editor = null } }
    val closeSheet: () -> Unit = remember(controller) { { showSheet = false } }

    // One coordinated enter/exit. The overlay's internal slide + scrim
    // share this fade's clock; exit is a touch quicker than entry. Re-opening
    // mid-exit reverses the running animation instead of queueing a new one.
    AnimatedVisibility(
        visible = showSheet,
        enter = fadeIn(MotionTokens.sheet(reduced)),
        exit = fadeOut(MotionTokens.content(reduced)),
    ) {
        TuningsOverlay(
            state = sheetState,
            initialSection = sheetSection,
            onClose = closeSheet,
            onPresetSelect = onPresetSelect,
            onToggleFavorite = onToggleFavorite,
            onConcertStep = onConcertStep,
            onConcertStepCoarse = onConcertStepCoarse,
            onConcertSet = onConcertSet,
            onAddTuning = { openEditor(null) },
            onEditTuning = { openEditor(it) },
            onDeleteTuning = onDeleteTuning,
            onSpellingSelect = onSpellingSelect,
            onImportRequested = onImportPicker,
            onExportRequested = onExportPicker,
            editor = editor,
            onEditorNameChange = onEditorNameChange,
            onEditorNoteCycle = onEditorNoteCycle,
            onEditorOctaveCycle = onEditorOctaveCycle,
            onEditorRemoveString = onEditorRemoveString,
            onEditorAddString = onEditorAddString,
            onEditorSave = saveEditor,
            onEditorCancel = onEditorCancel,
            platformBack = platformBack,
        )
    }

    pendingDelete?.let { target ->
        val p = dev.anima.resonance.ui.instrument.InstrumentTheme.palette
        val confirmDelete: () -> Unit = remember(target.id) {
            {
                pendingDelete = null
                scope.launch {
                    controller.deleteCustomPreset(target.id)
                    refresh()
                }
            }
        }
        val cancelDelete: () -> Unit = remember(controller) { { pendingDelete = null } }
        AlertDialog(
            onDismissRequest = cancelDelete,
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
                    onClick = confirmDelete,
                    modifier = Modifier.testTag("resonance.deleteDialog.confirm"),
                ) { Text("Delete", color = p.lampRed) }
            },
            dismissButton = {
                TextButton(
                    onClick = cancelDelete,
                    modifier = Modifier.testTag("resonance.deleteDialog.cancel"),
                ) { Text("Cancel", color = p.textMuted) }
            },
        )
    }
}
