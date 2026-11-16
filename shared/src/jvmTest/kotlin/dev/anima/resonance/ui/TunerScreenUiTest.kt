package dev.anima.resonance.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.anima.resonance.music.BuiltInTunings
import dev.anima.resonance.music.Note
import dev.anima.resonance.presentation.CustomEditorUiState
import dev.anima.resonance.presentation.EditorStringUi
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.MainScreenUi
import dev.anima.resonance.presentation.PresetSheetUi
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.presentation.TunerSnapshot
import dev.anima.resonance.presentation.TunerStatus
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * JVM-hosted Compose semantics tests. Fixed-faceplate contract: the main instrument is
 * a FIXED, non-scrolling faceplate (meter, readout, mode, strings, labeled
 * TUNINGS & SETTINGS key); configuration lives in [TuningsOverlay], which
 * scrolls and closes. Exceptionally constrained / large-font layouts use
 * an explicit scrollable fallback (resonance.scroll).
 */
class TunerScreenUiTest {

    @get:Rule
    val rule = createComposeRule()
    private fun state(
        status: TunerStatus,
        note: Note? = null,
        cents: Int = 0,
        permission: MicPermissionState = MicPermissionState.GRANTED,
        mode: TunerMode = TunerMode.CHROMATIC,
        preset: dev.anima.resonance.music.TuningPreset? = null,
        selected: Int? = null,
    ) = MainScreenUi.toUiState(
        snapshot = if (status == TunerStatus.LISTENING || status == TunerStatus.IDLE) null else
            TunerSnapshot(
                reading = dev.anima.resonance.presentation.TunerReading(
                    status, note, if (note == null) 0.0 else 82.4, cents,
                    if (cents < 0) dev.anima.resonance.presentation.PitchDirection.FLAT
                    else dev.anima.resonance.presentation.PitchDirection.SHARP,
                    false, 1.0,
                ),
                rms = 0.3,
                clipping = status == TunerStatus.CLIPPING,
                sampleRate = 48000,
            ),
        permission = permission,
        mode = mode,
        preset = preset,
        selectedStringIndex = selected,
    )

    private fun show(
        state: dev.anima.resonance.presentation.MainScreenUiState,
        onModeSelect: (TunerMode) -> Unit = {},
        onStringSelect: (Int) -> Unit = {},
        onPermissionCta: () -> Unit = {},
        onRequestPermission: () -> Unit = {},
        onRetryCapture: () -> Unit = {},
        onOpenSheet: () -> Unit = {},
    ) {
        rule.setContent {
            ResonanceTheme {
                TunerScreen(
                    state = state,
                    onModeSelect = onModeSelect,
                    onStringSelect = onStringSelect,
                    onPermissionCta = onPermissionCta,
                    onRequestPermission = onRequestPermission,
                    onRetryCapture = onRetryCapture,
                    onOpenSheet = onOpenSheet,
                )
            }
        }
    }

    /** Renders the configuration overlay directly (as MainScreen does). */
    private fun showOverlay(
        state: dev.anima.resonance.presentation.MainScreenUiState,
        onClose: () -> Unit = {},
        onToggleFavorite: (String) -> Unit = {},
        onPresetSelect: (String) -> Unit = {},
        onConcertStep: (Boolean) -> Unit = {},
        onConcertStepCoarse: (Boolean) -> Unit = {},
        onConcertSet: (Double) -> Unit = {},
        onSpellingSelect: (dev.anima.resonance.music.Spelling) -> Unit = {},
        editor: CustomEditorUiState? = null,
        onEditorNameChange: (String) -> Unit = {},
        onEditorNoteCycle: (Int, Boolean) -> Unit = { _, _ -> },
        onEditorOctaveCycle: (Int, Boolean) -> Unit = { _, _ -> },
        onEditorRemoveString: (Int) -> Unit = {},
        onEditorAddString: () -> Unit = {},
        onEditorSave: () -> Unit = {},
        onEditorCancel: () -> Unit = {},
    ) {
        rule.setContent {
            ResonanceTheme {
                TuningsOverlay(
                    state = state,
                    onClose = onClose,
                    onToggleFavorite = onToggleFavorite,
                    onPresetSelect = onPresetSelect,
                    onConcertStep = onConcertStep,
                    onConcertStepCoarse = onConcertStepCoarse,
                    onConcertSet = onConcertSet,
                    onSpellingSelect = onSpellingSelect,
                    editor = editor,
                    onEditorNameChange = onEditorNameChange,
                    onEditorNoteCycle = onEditorNoteCycle,
                    onEditorOctaveCycle = onEditorOctaveCycle,
                    onEditorRemoveString = onEditorRemoveString,
                    onEditorAddString = onEditorAddString,
                    onEditorSave = onEditorSave,
                    onEditorCancel = onEditorCancel,
                )
            }
        }
    }

    private fun overlayScroll() = rule.onNodeWithTag("resonance.overlay.scroll")

    private fun custom(id: String, name: String, vararg labels: String) =
        dev.anima.resonance.music.TuningPreset(
            id = id,
            name = name,
            builtIn = false,
            strings = labels.mapIndexed { i, l ->
                dev.anima.resonance.music.TuningString("$id-s${i + 1}", Note.fromLabel(l))
            },
        )

    // ── Main instrument (fixed faceplate) ─────────────────────────────────

    @Test
    fun listeningStateShowsHintAndDialNotNote() {
        show(state(status = TunerStatus.LISTENING))
        rule.onNodeWithTag("resonance.dial").assertIsDisplayed()
        rule.onNodeWithTag("resonance.hint").assertIsDisplayed()
        rule.onNodeWithTag("resonance.note").assertDoesNotExist()
    }

    @Test
    fun flatE2InstrumentModeShowsNoteCentsAndStrings() {
        show(
            state(
                status = TunerStatus.MEASURED,
                note = Note.fromLabel("E2"),
                cents = -3,
                mode = TunerMode.INSTRUMENT,
                preset = BuiltInTunings.standard,
                selected = 0,
            ),
        )
        rule.onNodeWithTag("resonance.note").assertIsDisplayed()
        rule.onNodeWithTag("resonance.cents").assertIsDisplayed()
        rule.onNodeWithTag("resonance.string.0").assertIsDisplayed()
        rule.onNodeWithTag("resonance.string.5").assertIsDisplayed()
        rule.onNodeWithTag("resonance.hint").assertDoesNotExist()
    }

    @Test
    fun chromaticModeHasNoStringNodes() {
        show(state(status = TunerStatus.MEASURED, note = Note.fromLabel("E2"), cents = -3))
        rule.onNodeWithTag("resonance.note").assertIsDisplayed()
        rule.onNodeWithTag("resonance.string.0").assertDoesNotExist()
    }

    @Test
    fun permanentDenialShowsPermissionCtaOnly() {
        show(state(status = TunerStatus.LISTENING, permission = MicPermissionState.DENIED_PERMANENT))
        rule.onNodeWithTag("resonance.permission").assertIsDisplayed()

        show(state(status = TunerStatus.LISTENING, permission = MicPermissionState.GRANTED))
        rule.onNodeWithTag("resonance.permission").assertDoesNotExist()
    }

    @Test
    fun clippingStateStillShowsDial() {
        show(state(status = TunerStatus.CLIPPING, note = Note.fromLabel("E2"), cents = -3))
        rule.onNodeWithTag("resonance.dial").assertIsDisplayed()
        rule.onNodeWithTag("resonance.hint").assertIsDisplayed()
    }

    @Test
    fun mainScreenIsFixedAndHasNoInlineSheetOrHeader() {
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
        )
        show(ui)
        // No scrolling root at normal sizes, no inline configuration, no
        // legacy header controls — just the instrument and its labeled key.
        rule.onNodeWithTag("resonance.scroll").assertDoesNotExist()
        rule.onNodeWithTag("resonance.sheet").assertDoesNotExist()
        rule.onNodeWithTag("resonance.openTunings").assertDoesNotExist()
        rule.onNodeWithTag("resonance.settings").assertDoesNotExist()
        rule.onNodeWithTag("resonance.openSheet").assertIsDisplayed()
        rule.onNodeWithTag("resonance.preset").assertIsDisplayed()
        rule.onNodeWithTag("resonance.concert").assertIsDisplayed()
        rule.onNodeWithTag("resonance.micStatus").assertIsDisplayed()
    }

    @Test
    fun tuningsKeyOpensTheOverlayViaCallback() {
        var opened = 0
        show(state(status = TunerStatus.LISTENING), onOpenSheet = { opened++ })
        rule.onNodeWithTag("resonance.openSheet").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun stringTargetsAreClickableAndExposeSelectionState() {
        val clicks = mutableListOf<Int>()
        show(
            state = state(
                status = TunerStatus.MEASURED,
                note = Note.fromLabel("E2"),
                cents = -3,
                mode = TunerMode.INSTRUMENT,
                preset = BuiltInTunings.standard,
                selected = 0,
            ),
            onStringSelect = { clicks += it },
        )
        rule.onNodeWithTag("resonance.string.0").assertIsSelected()
        rule.onNodeWithTag("resonance.string.2").performClick()
        assertEquals(listOf(2), clicks, "tapping the third string must lock index 2")
        rule.onNodeWithTag("resonance.string.2")
            .assertContentDescriptionEquals("String 3, D3")
    }

    @Test
    fun autoDetectedStringIsVisibleAndSemanticallyDifferentFromALock() {
        show(
            state = state(
                status = TunerStatus.MEASURED,
                note = Note.fromLabel("E2"),
                cents = -3,
                mode = TunerMode.INSTRUMENT,
                preset = BuiltInTunings.standard,
                selected = null,
            ),
        )
        rule.onNodeWithTag("resonance.note")
            .assertContentDescriptionEquals("Target note E2")
        rule.onNodeWithTag("resonance.string.0")
            .assertContentDescriptionEquals("String 1, E2, detected")
        // AUTO is an observation, not a user selection.
        rule.onNodeWithTag("resonance.string.0").assertIsNotSelected()
    }

    @Test
    fun softDenialCtaRequestsPermissionWhilePermanentDenialOpensSettings() {
        val requests = mutableListOf<String>()
        show(
            state = state(status = TunerStatus.LISTENING, permission = MicPermissionState.DENIED),
            onPermissionCta = { requests += "settings" },
            onRequestPermission = { requests += "request" },
        )
        rule.onNodeWithTag("resonance.permission").performClick()
        assertEquals(listOf("request"), requests)

        show(
            state = state(status = TunerStatus.LISTENING, permission = MicPermissionState.DENIED_PERMANENT),
            onPermissionCta = { requests += "settings" },
            onRequestPermission = { requests += "request" },
        )
        rule.onNodeWithTag("resonance.permission").performClick()
        assertEquals(listOf("request", "settings"), requests)
    }

    @Test
    fun modeOptionsExposeAndInvokeExplicitChoices() {
        val selected = mutableListOf<TunerMode>()
        show(
            state = state(status = TunerStatus.LISTENING, mode = TunerMode.CHROMATIC),
            onModeSelect = { selected += it },
        )
        rule.onNodeWithTag("resonance.mode.chromatic").assertIsSelected()
        rule.onNodeWithTag("resonance.mode.instrument").performClick()
        assertEquals(listOf(TunerMode.INSTRUMENT), selected)
    }

    @Test
    fun spokenReadingCombinesNoteAndOctaveAndKeepsSignMeaning() {
        show(state(status = TunerStatus.MEASURED, note = Note.fromLabel("E2"), cents = -3))
        rule.onNodeWithTag("resonance.note")
            .assertContentDescriptionEquals("Detected note E2")
        rule.onNodeWithTag("resonance.cents")
            .assertContentDescriptionEquals("3 cents flat, tune up")

        show(
            state = state(
                status = TunerStatus.MEASURED,
                note = Note.fromLabel("A4"),
                cents = 0,
            ).copy(inTune = true, directionText = "In tune"),
        )
        rule.onNodeWithTag("resonance.cents")
            .assertContentDescriptionEquals("In tune")
    }

    @Test
    fun smallScreenLargeTextFallsBackToScrollWithoutClipping() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                ResonanceTheme {
                    Box(Modifier.size(width = 320.dp, height = 480.dp)) {
                        TunerScreen(
                            state = state(
                                status = TunerStatus.MEASURED,
                                note = Note.fromLabel("E2"),
                                cents = -3,
                                mode = TunerMode.INSTRUMENT,
                                preset = BuiltInTunings.standard,
                                selected = 0,
                            ).copy(
                                presetRows = PresetSheetUi.toRows(
                                    BuiltInTunings.all,
                                    setOf("standard"),
                                    "standard",
                                ),
                            ),
                            onModeSelect = {},
                            onStringSelect = {},
                            onPermissionCta = {},
                            onRetryCapture = {},
                        )
                    }
                }
            }
        }
        // Explicit accessible fallback: everything reachable by scrolling.
        val scroll = rule.onNodeWithTag("resonance.scroll")
        rule.onNodeWithTag("resonance.note").assertIsDisplayed()
        scroll.performScrollToNode(hasTestTag("resonance.mode.instrument"))
        rule.onNodeWithTag("resonance.mode.instrument").assertIsDisplayed()
        scroll.performScrollToNode(hasTestTag("resonance.string.5"))
        rule.onNodeWithTag("resonance.string.5").assertIsDisplayed()
        scroll.performScrollToNode(hasTestTag("resonance.openSheet"))
        rule.onNodeWithTag("resonance.openSheet").assertIsDisplayed()
    }

    @Test
    fun readingSurfaceNeverShowsAZeroHzOrCenteredZeroForWeakSignal() {
        val weak = MainScreenUi.toUiState(
            snapshot = TunerSnapshot(
                reading = dev.anima.resonance.presentation.TunerReading(
                    TunerStatus.INSUFFICIENT_SIGNAL,
                    Note.fromLabel("E2"),
                    0.0,
                    0,
                    dev.anima.resonance.presentation.PitchDirection.FLAT,
                    false,
                    0.2,
                ),
                rms = 0.001,
                clipping = false,
                sampleRate = 48000,
            ),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
            captureActive = true,
        )
        show(weak)
        rule.onNodeWithTag("resonance.note").assertDoesNotExist()
        rule.onNodeWithTag("resonance.frequency").assertDoesNotExist()
        rule.onNodeWithTag("resonance.cents").assertContentDescriptionEquals("No current reading")
        rule.onNodeWithTag("resonance.direction").assertDoesNotExist()
        rule.onNodeWithTag("resonance.hint").assertIsDisplayed()
    }

    // ── Configuration overlay ─────────────────────────────────────────────

    @Test
    fun overlayShowsRowsSelectionAndConcertError() {
        val c1 = custom("custom-1", "My Open D", "D2", "A2", "D3", "F#3", "A3", "D4")
        val state = MainScreenUi.toUiState(
            snapshot = null,
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
            concertPitchHz = 399.0,
            presets = BuiltInTunings.all + c1,
            favoritePresetIds = setOf("standard"),
            selectedPresetId = "custom-1",
        )
        showOverlay(state)
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.concertError"))
        rule.onNodeWithTag("resonance.sheet.concertError").assertIsDisplayed()
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.row.standard"))
        rule.onNodeWithTag("resonance.sheet.row.standard").assertIsDisplayed()
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.row.custom-1"))
        rule.onNodeWithTag("resonance.sheet.row.custom-1").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.title").assertExists()
    }

    @Test
    fun overlayCloseKeyInvokesDismiss() {
        var closed = 0
        showOverlay(state(status = TunerStatus.LISTENING), onClose = { closed++ })
        rule.onNodeWithTag("resonance.overlay.close").assertIsDisplayed()
        rule.onNodeWithTag("resonance.overlay.close").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun overlayConcertSteppersInvokeCallback() {
        val calls = mutableListOf<Boolean>()
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
            concertLabel = "A4 = 440 Hz",
        )
        showOverlay(ui, onConcertStep = { calls.add(it) })
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.concert.plus"))
        rule.onNodeWithTag("resonance.sheet.concert.minus").performClick()
        rule.onNodeWithTag("resonance.sheet.concert.plus").performClick()
        rule.onNodeWithTag("resonance.sheet.concert.plus").performClick()
        assertEquals(listOf(false, true, true), calls, "minus, then plus twice")
    }

    @Test
    fun overlayCoarseStepSelectionRoutesToOneHertzCallback() {
        val calls = mutableListOf<Boolean>()
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
            concertLabel = "A4 = 440 Hz",
        )
        showOverlay(ui, onConcertStepCoarse = { calls += it })
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.concert.step.coarse"))
        // Select the 1 Hz step, then the shared −/+ keys route to it.
        rule.onNodeWithTag("resonance.sheet.concert.step.coarse").performClick()
        rule.onNodeWithTag("resonance.sheet.concert.minus").performClick()
        rule.onNodeWithTag("resonance.sheet.concert.plus").performClick()
        assertEquals(listOf(false, true), calls)
    }

    @Test
    fun overlayResetAppearsOnlyWhenReferenceDiffersAndResetsTo440() {
        val sets = mutableListOf<Double>()
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
            concertPitchHz = 442.0,
        )
        showOverlay(ui, onConcertSet = { sets += it })
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.concert.reset"))
        rule.onNodeWithTag("resonance.sheet.concert.reset").performClick()
        assertEquals(listOf(440.0), sets)
    }

    @Test
    fun overlayResetHiddenAt440() {
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
            concertPitchHz = 440.0,
        )
        showOverlay(ui)
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.concert.value"))
        rule.onNodeWithTag("resonance.sheet.concert.reset").assertDoesNotExist()
    }

    @Test
    fun overlayDirectEditAppliesValidValue() {
        val sets = mutableListOf<Double>()
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
        )
        showOverlay(ui, onConcertSet = { sets += it })
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.concert.value"))
        rule.onNodeWithTag("resonance.sheet.concert.value").performClick()
        rule.onNodeWithTag("resonance.sheet.concert.input").performTextClearance()
        rule.onNodeWithTag("resonance.sheet.concert.input").performTextInput("442.5")
        rule.onNodeWithTag("resonance.sheet.concert.apply").performClick()
        assertEquals(listOf(442.5), sets)
    }

    @Test
    fun overlayDirectEditRejectsOutOfRangeWithoutCoercing() {
        val sets = mutableListOf<Double>()
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
        )
        showOverlay(ui, onConcertSet = { sets += it })
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.concert.value"))
        rule.onNodeWithTag("resonance.sheet.concert.value").performClick()
        rule.onNodeWithTag("resonance.sheet.concert.input").performTextClearance()
        rule.onNodeWithTag("resonance.sheet.concert.input").performTextInput("500")
        rule.onNodeWithTag("resonance.sheet.concert.apply").performClick()
        assertEquals(emptyList<Double>(), sets, "out-of-range input is never applied")
        rule.onNodeWithTag("resonance.sheet.concert.editError").assertIsDisplayed()
    }

    @Test
    fun overlayEditorRendersRowsAndControls() {
        val ui = CustomEditorUiState(
            name = "My Open D",
            strings = listOf(
                EditorStringUi("D", 2),
                EditorStringUi("F#", 3),
            ),
        )
        showOverlay(state(status = TunerStatus.LISTENING), editor = ui)
        rule.onNodeWithTag("resonance.editor").assertIsDisplayed()
        rule.onNodeWithTag("resonance.editor.name").assertIsDisplayed()
        rule.onNodeWithTag("resonance.editor.string.0.note").assertIsDisplayed()
        rule.onNodeWithTag("resonance.editor.string.1.octave").assertIsDisplayed()
        rule.onNodeWithTag("resonance.editor.add").assertIsDisplayed()
        rule.onNodeWithTag("resonance.editor.save").assertIsDisplayed()
        rule.onNodeWithTag("resonance.editor.cancel").assertIsDisplayed()
    }

    @Test
    fun overlayEditorButtonsInvokeFormCallbacks() {
        val events = mutableListOf<String>()
        val ui = CustomEditorUiState(
            name = "X",
            strings = listOf(EditorStringUi("E", 2)),
        )
        showOverlay(
            state(status = TunerStatus.LISTENING),
            editor = ui,
            onEditorNameChange = { events += "name:$it" },
            onEditorNoteCycle = { i, up -> events += "note:$i:$up" },
            onEditorOctaveCycle = { i, up -> events += "octave:$i:$up" },
            onEditorRemoveString = { i -> events += "remove:$i" },
            onEditorAddString = { events += "add" },
            onEditorSave = { events += "save" },
            onEditorCancel = { events += "cancel" },
        )
        rule.onNodeWithTag("resonance.editor.string.0.note").performClick()
        rule.onNodeWithTag("resonance.editor.string.0.octave").performClick()
        rule.onNodeWithTag("resonance.editor.string.0.remove").performClick()
        rule.onNodeWithTag("resonance.editor.add").performClick()
        rule.onNodeWithTag("resonance.editor.save").performClick()
        rule.onNodeWithTag("resonance.editor.cancel").performClick()
        assertEquals(
            listOf("note:0:true", "octave:0:true", "remove:0", "add", "save", "cancel"),
            events,
        )
    }

    @Test
    fun overlayEditorShowsValidationErrors() {
        val ui = CustomEditorUiState(name = "", strings = emptyList(), errors = listOf("Name is required"))
        showOverlay(state(status = TunerStatus.LISTENING), editor = ui)
        rule.onNodeWithTag("resonance.editor.error").assertIsDisplayed()
    }

    @Test
    fun overlayEditorVisibleOnlyWhenStateProvided() {
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
        )
        showOverlay(ui)
        rule.onNodeWithTag("resonance.editor").assertDoesNotExist()

        showOverlay(ui, editor = CustomEditorUiState(name = "X", strings = listOf(EditorStringUi("E", 2))))
        overlayScroll().performScrollToNode(hasTestTag("resonance.editor"))
        rule.onNodeWithTag("resonance.editor").assertIsDisplayed()
    }

    @Test
    fun overlayExposesAddTuningAndCustomRowActions() {
        val customPreset = dev.anima.resonance.music.TuningPreset(
            id = "custom-1", name = "My Open D", builtIn = false,
            strings = listOf(
                dev.anima.resonance.music.TuningString("c1-s1", Note.fromLabel("D2")),
                dev.anima.resonance.music.TuningString("c1-s2", Note.fromLabel("A2")),
            ),
        )
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all + customPreset, setOf("standard"), "standard"),
        )
        showOverlay(ui)
        // Footer actions are fixed (always visible), not in the scroll body.
        rule.onNodeWithTag("resonance.sheet.add").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.import").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.export").assertIsDisplayed()
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.row.custom-1"))
        rule.onNodeWithTag("resonance.sheet.row.custom-1.edit").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.row.custom-1.delete").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.row.standard.edit").assertDoesNotExist() // built-ins are read-only
    }

    @Test
    fun overlaySpellingControlInvokesCallback() {
        val calls = mutableListOf<dev.anima.resonance.music.Spelling>()
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
        )
        showOverlay(ui, onSpellingSelect = { calls.add(it) })
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.spelling.sharp"))
        rule.onNodeWithTag("resonance.sheet.spelling.sharp").performClick()
        rule.onNodeWithTag("resonance.sheet.spelling.flat").performClick()
        assertEquals(
            listOf(dev.anima.resonance.music.Spelling.SHARP, dev.anima.resonance.music.Spelling.FLAT),
            calls,
        )
    }

    @Test
    fun overlayShowsImportResultText() {
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
            importResultText = "Imported 2 tunings",
        )
        showOverlay(ui)
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.importResult"))
        rule.onNodeWithTag("resonance.sheet.importResult").assertIsDisplayed()
    }

    @Test
    fun overlayWithoutRowsHasNoPresetList() {
        showOverlay(state(status = TunerStatus.LISTENING))
        rule.onNodeWithTag("resonance.sheet.row.standard").assertDoesNotExist()
    }

    @Test
    fun overlayFavoriteToggleIsARealNamedControl() {
        val clicks = mutableListOf<String>()
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, emptySet(), "standard"),
        )
        showOverlay(ui, onToggleFavorite = { clicks += it })
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.row.standard.favorite"))
        val favorite = rule.onNodeWithTag("resonance.sheet.row.standard.favorite")
        favorite.assertContentDescriptionEquals("Add Standard to favorites")
        favorite.performClick()
        assertEquals(listOf("standard"), clicks)
    }
}