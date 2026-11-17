package dev.anima.resonance.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import kotlin.test.assertTrue
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
        onOpenSettings: () -> Unit = {},
        onAutoSelectionChange: (Boolean) -> Unit = {},
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
                    onOpenSettings = onOpenSettings,
                    onAutoSelectionChange = onAutoSelectionChange,
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
        initialSection: dev.anima.resonance.presentation.SheetSection? = null,
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
                    initialSection = initialSection,
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
        rule.onNodeWithTag("resonance.preset", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("resonance.concert", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("resonance.micStatus").assertIsDisplayed()
    }

    @Test
    fun theTuningNameOpensThePresetListInInstrumentMode() {
        var opened = 0
        show(
            state = state(
                status = TunerStatus.LISTENING,
                mode = TunerMode.INSTRUMENT,
                preset = BuiltInTunings.standard,
            ),
            onOpenSheet = { opened++ },
        )
        rule.onNodeWithTag("resonance.openSheet").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theChromaticSettingsEntryOpensTheCalibrationDestination() {
        var settings = 0
        var presets = 0
        show(
            state = state(status = TunerStatus.LISTENING),
            onOpenSheet = { presets++ },
            onOpenSettings = { settings++ },
        )
        rule.onNodeWithTag("resonance.openSheet").performClick()
        assertEquals(1, settings, "the chromatic entry is the calibration entry")
        assertEquals(0, presets)
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

    // ── Control deck entry points, selection affordance, mode stability ──

    @Test
    fun automaticSelectionToggleReflectsAndInvertsTheRealSelectionState() {
        val changes = mutableListOf<Boolean>()
        show(
            state = state(
                status = TunerStatus.MEASURED,
                note = Note.fromLabel("E2"),
                cents = -3,
                mode = TunerMode.INSTRUMENT,
                preset = BuiltInTunings.standard,
                selected = null,
            ),
            onAutoSelectionChange = { changes += it },
        )
        // No manual lock → the engine IS automatic.
        rule.onNodeWithTag("resonance.autoSelection").assertIsOn()
        rule.onNodeWithTag("resonance.autoSelection").performClick()
        assertEquals(listOf(false), changes, "turning automatic off must be requested")

        changes.clear()
        show(
            state = state(
                status = TunerStatus.MEASURED,
                note = Note.fromLabel("E2"),
                cents = -3,
                mode = TunerMode.INSTRUMENT,
                preset = BuiltInTunings.standard,
                selected = 2,
            ),
            onAutoSelectionChange = { changes += it },
        )
        rule.onNodeWithTag("resonance.autoSelection").assertIsOff()
        rule.onNodeWithTag("resonance.autoSelection").performClick()
        assertEquals(listOf(true), changes, "turning automatic on must be requested")
    }

    @Test
    fun theConfigurationRowAndTheTuningNameHaveSeparateEntries() {
        var presets = 0
        var settings = 0
        show(
            state = state(
                status = TunerStatus.MEASURED,
                note = Note.fromLabel("E2"),
                cents = -3,
                mode = TunerMode.INSTRUMENT,
                preset = BuiltInTunings.standard,
            ),
            onOpenSheet = { presets++ },
            onOpenSettings = { settings++ },
        )
        rule.onNodeWithTag("resonance.openReference").performClick()
        assertEquals(1, settings, "the configuration row opens the calibration destination")
        assertEquals(0, presets)
        rule.onNodeWithTag("resonance.openSheet").performClick()
        assertEquals(1, presets, "the tuning name opens the preset list")
    }

    @Test
    fun theTuningNameShowsTheActualPresetNotAHardcodedStandard() {
        val custom = custom("custom-long", "DADGAD Low", "D2", "A2", "D3", "G3", "A3", "D4")
        show(
            state = state(
                status = TunerStatus.LISTENING,
                mode = TunerMode.INSTRUMENT,
                preset = custom,
            ),
        )
        rule.onNodeWithTag("resonance.preset", useUnmergedTree = true).assertTextEquals("DADGAD Low")
        rule.onNodeWithTag("resonance.openReference").assertIsDisplayed()
    }

    @Test
    fun aLongCustomPresetNameDoesNotPushTheSettingsKeyOffScreen() {
        val long = custom(
            "custom-very-long",
            "Open D Modal Slip-Jig Tuning For Twelve String",
            "D2", "A2", "D3", "F#3", "A3", "D4",
        )
        show(
            state = state(
                status = TunerStatus.LISTENING,
                mode = TunerMode.INSTRUMENT,
                preset = long,
            ),
        )
        rule.onNodeWithTag("resonance.preset", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("resonance.openReference").assertIsDisplayed()
    }

    @Test
    fun theQuietCalibrationLineShowsTheConfiguredReferencePitchAndOpensIt() {
        var settings = 0
        show(
            state = state(
                status = TunerStatus.LISTENING,
                mode = TunerMode.INSTRUMENT,
                preset = BuiltInTunings.standard,
            ).copy(concertPitchHz = 442.0),
            onOpenSettings = { settings++ },
        )
        // The actual configured value, never a hardcoded "A4 440 Hz".
        rule.onNodeWithTag("resonance.concert", useUnmergedTree = true).assertTextEquals("A4 = 442 Hz")
        rule.onNodeWithTag("resonance.openReference").performClick()
        assertEquals(1, settings, "the calibration line opens the reference-pitch section")
    }

    @Test
    fun chromaticModeKeepsTheMeterAndReplacesGuitarControlsWithASummary() {
        val ui = state(status = TunerStatus.MEASURED, note = Note.fromLabel("E2"), cents = -3)
            .copy(concertPitchHz = 440.0, accidentalSpelling = dev.anima.resonance.music.Spelling.FLAT)
        show(ui)
        // Instrument-only controls are hidden, not shown disabled.
        rule.onNodeWithTag("resonance.autoSelection").assertDoesNotExist()
        rule.onNodeWithTag("resonance.string.0").assertDoesNotExist()
        // ONE settings entry (no duplicate gear in this mode) plus the
        // read-only calibration plate.
        rule.onNodeWithTag("resonance.openSheet").assertIsDisplayed()
        rule.onNodeWithTag("resonance.openReference").assertDoesNotExist()
        rule.onNodeWithTag("resonance.concert", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("resonance.concert", useUnmergedTree = true).assertTextEquals("A4 = 440 Hz")
        rule.onNodeWithTag("resonance.spelling", useUnmergedTree = true).assertTextEquals("Flats ♭")
    }

    @Test
    fun switchingModesDoesNotMoveOrResizeTheMeterAndReadout() {
        val instrument = state(
            status = TunerStatus.MEASURED,
            note = Note.fromLabel("E2"),
            cents = -3,
            mode = TunerMode.INSTRUMENT,
            preset = BuiltInTunings.standard,
        )
        show(instrument)
        val dialIn = rule.onNodeWithTag("resonance.dial").getUnclippedBoundsInRoot()
        val noteIn = rule.onNodeWithTag("resonance.note").getUnclippedBoundsInRoot()

        show(instrument.copy(mode = TunerMode.CHROMATIC, presetName = null, targetLabels = emptyList()))
        val dialCh = rule.onNodeWithTag("resonance.dial").getUnclippedBoundsInRoot()
        val noteCh = rule.onNodeWithTag("resonance.note").getUnclippedBoundsInRoot()

        assertEquals(dialIn.top.value, dialCh.top.value, "dial top must not move between modes")
        assertEquals(dialIn.bottom.value, dialCh.bottom.value, "dial height must not change between modes")
        assertEquals(noteIn.top.value, noteCh.top.value, "readout must not move between modes")
        assertEquals(noteIn.bottom.value, noteCh.bottom.value, "readout must not resize between modes")

        // The mode selector is the anchor the user switches with: it must stay
        // exactly where it is, and the chromatic block must be visibly shorter
        // than the instrument block instead of a reserved empty panel.
        show(instrument)
        val modeIn = rule.onNodeWithTag("resonance.mode").getUnclippedBoundsInRoot()
        val referenceIn = rule.onNodeWithTag("resonance.openReference").getUnclippedBoundsInRoot()
        show(instrument.copy(mode = TunerMode.CHROMATIC, presetName = null, targetLabels = emptyList()))
        val modeCh = rule.onNodeWithTag("resonance.mode").getUnclippedBoundsInRoot()
        assertEquals(modeIn.top.value, modeCh.top.value, "mode selector must not move between modes")
        assertEquals(modeIn.bottom.value, modeCh.bottom.value, "mode selector must not resize between modes")
        rule.onNodeWithTag("resonance.autoSelection").assertDoesNotExist()
        rule.onNodeWithTag("resonance.openReference").assertDoesNotExist()
        assertTrue(
            referenceIn.bottom.value > modeCh.bottom.value,
            "instrument mode keeps the reference row inside its taller block",
        )
    }

    @Test
    fun deckControlsShareOneContentAlignmentLine() {
        show(
            state = state(
                status = TunerStatus.MEASURED,
                note = Note.fromLabel("E2"),
                cents = -3,
                mode = TunerMode.INSTRUMENT,
                preset = BuiltInTunings.standard,
            ),
        )
        // Press layers may run full-bleed inside the deck, but every
        // control's CONTENT still sits on the one shared gutter line — mode
        // track, tuning name and the first string cell must left-align.
        val mode = rule.onNodeWithTag("resonance.mode").getUnclippedBoundsInRoot()
        val preset = rule.onNodeWithTag("resonance.preset", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val string0 = rule.onNodeWithTag("resonance.string.0").getUnclippedBoundsInRoot()
        assertEquals(mode.left.value, string0.left.value, "string rail must align with the mode track")
        assertEquals(mode.left.value, preset.left.value, "tuning name must align with the mode track")
    }

    @Test
    fun everyStringTargetMeetsTheTouchFloor() {
        show(
            state = state(
                status = TunerStatus.MEASURED,
                note = Note.fromLabel("E2"),
                cents = -3,
                mode = TunerMode.INSTRUMENT,
                preset = BuiltInTunings.standard,
            ),
        )
        (0 until 6).forEach { i ->
            rule.onNodeWithTag("resonance.string.$i")
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
        }
        rule.onNodeWithTag("resonance.openReference").assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag("resonance.openReference").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("resonance.autoSelection").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("resonance.mode.instrument").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun aCompactPhoneKeepsTheFixedLayoutAndAllSixStringsOnScreen() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
                ResonanceTheme {
                    Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                        TunerScreen(
                            state = state(
                                status = TunerStatus.MEASURED,
                                note = Note.fromLabel("E2"),
                                cents = -3,
                                mode = TunerMode.INSTRUMENT,
                                preset = BuiltInTunings.standard,
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
        // Still the fixed instrument (no scrolling fallback at normal text size).
        rule.onNodeWithTag("resonance.scroll").assertDoesNotExist()
        rule.onNodeWithTag("resonance.dial").assertIsDisplayed()
        rule.onNodeWithTag("resonance.note").assertIsDisplayed()
        rule.onNodeWithTag("resonance.cents").assertIsDisplayed()
        rule.onNodeWithTag("resonance.mode.instrument").assertIsDisplayed()
        rule.onNodeWithTag("resonance.string.0").assertIsDisplayed()
        rule.onNodeWithTag("resonance.string.5").assertIsDisplayed()
        // The quiet calibration line yields its row rather than crushing the
        // meter; the settings key and the reference remain reachable.
        rule.onNodeWithTag("resonance.openReference").assertDoesNotExist()
        rule.onNodeWithTag("resonance.openSheet").assertIsDisplayed()
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
        // Destination 1 shows ONLY tunings …
        showOverlay(state)
        rule.onNodeWithTag("resonance.sheet.title").assertExists()
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.row.standard"))
        rule.onNodeWithTag("resonance.sheet.row.standard").assertIsDisplayed()
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.row.custom-1"))
        rule.onNodeWithTag("resonance.sheet.row.custom-1").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.concertError").assertDoesNotExist()
        rule.onNodeWithTag("resonance.sheet.concert.value").assertDoesNotExist()
        // … and destination 2 shows ONLY calibration.
        showOverlay(state, initialSection = dev.anima.resonance.presentation.SheetSection.CALIBRATION)
        rule.onNodeWithTag("resonance.sheet.concertError").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.row.standard").assertDoesNotExist()
        rule.onNodeWithTag("resonance.sheet.row.custom-1").assertDoesNotExist()
    }

    @Test
    fun overlayOpensScrolledToTheSettingsSectionOnAShortScreen() {
        val ui = state(status = TunerStatus.LISTENING, mode = TunerMode.INSTRUMENT).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
        )
        rule.setContent {
            ResonanceTheme {
                androidx.compose.foundation.layout.Box(Modifier.size(width = 360.dp, height = 560.dp)) {
                    TuningsOverlay(
                        state = ui,
                        onClose = {},
                        initialSection = dev.anima.resonance.presentation.SheetSection.CALIBRATION,
                    )
                }
            }
        }
        // No manual scrolling: the entry point lands directly on the calibration
        // destination (its reference pitch is in view; the rest scrolls).
        rule.onNodeWithTag("resonance.sheet.concert.value").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.row.standard").assertDoesNotExist()
    }

    @Test
    fun overlayOpensScrolledToTheTuningsSectionOnAShortScreen() {
        val ui = state(status = TunerStatus.LISTENING, mode = TunerMode.CHROMATIC).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
        )
        rule.setContent {
            ResonanceTheme {
                androidx.compose.foundation.layout.Box(Modifier.size(width = 360.dp, height = 560.dp)) {
                    TuningsOverlay(
                        state = ui,
                        onClose = {},
                        initialSection = dev.anima.resonance.presentation.SheetSection.TUNING,
                    )
                }
            }
        }
        rule.onNodeWithTag("resonance.sheet.row.standard").assertIsDisplayed()
    }

    @Test
    fun theSettingsHeaderKeepsItsTitleOnOneLineAndDoneContentSized() {
        showOverlay(
            state = state(status = TunerStatus.LISTENING),
            initialSection = dev.anima.resonance.presentation.SheetSection.MANAGE,
        )
        val sheet = rule.onNodeWithTag("resonance.overlay").getUnclippedBoundsInRoot()
        val title = rule.onNodeWithTag("resonance.sheet.title").getUnclippedBoundsInRoot()
        val done = rule.onNodeWithTag("resonance.overlay.close").getUnclippedBoundsInRoot()
        // Regression: the header used to collapse the title into a one-letter-wide
        // column while "Done" expanded across the sheet.
        val titleH = (title.bottom - title.top).value
        val titleW = (title.right - title.left).value
        val doneW = (done.right - done.left).value
        val sheetW = (sheet.right - sheet.left).value
        assertTrue(titleH < 40f, "title must stay on one line (was $titleH dp tall)")
        assertTrue(titleW > 60f, "title must keep a real width (was $titleW dp)")
        assertTrue(doneW < sheetW * 0.45f, "Done must be content-sized (was $doneW of $sheetW dp)")
        val centreDelta = kotlin.math.abs(
            ((done.top + done.bottom) - (title.top + title.bottom)).value / 2f,
        )
        assertTrue(centreDelta < 12f, "title and Done must be vertically centred in one row")
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
        showOverlay(ui, onConcertStep = { calls.add(it) }, initialSection = dev.anima.resonance.presentation.SheetSection.CALIBRATION)
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
        showOverlay(ui, onConcertStepCoarse = { calls += it }, initialSection = dev.anima.resonance.presentation.SheetSection.CALIBRATION)
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
        showOverlay(ui, onConcertSet = { sets += it }, initialSection = dev.anima.resonance.presentation.SheetSection.CALIBRATION)
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
        showOverlay(ui, initialSection = dev.anima.resonance.presentation.SheetSection.CALIBRATION)
        rule.onNodeWithTag("resonance.sheet.concert.value").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.concert.reset").assertDoesNotExist()
    }

    @Test
    fun overlayDirectEditAppliesValidValue() {
        val sets = mutableListOf<Double>()
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
        )
        showOverlay(ui, onConcertSet = { sets += it }, initialSection = dev.anima.resonance.presentation.SheetSection.CALIBRATION)
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
        showOverlay(ui, onConcertSet = { sets += it }, initialSection = dev.anima.resonance.presentation.SheetSection.CALIBRATION)
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
        // Creation/import/export live in the "Manage" destination, not in a
        // permanent footer competing with calibration.
        rule.onNodeWithTag("resonance.sheet.dest.manage").performClick()
        rule.onNodeWithTag("resonance.sheet.add").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.import").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.export").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.row.custom-1.edit").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.row.custom-1.delete").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.row.standard.edit").assertDoesNotExist() // built-ins are read-only
        // The Tuning destination selects tunings; it never shows management.
        rule.onNodeWithTag("resonance.sheet.dest.tuning").performClick()
        overlayScroll().performScrollToNode(hasTestTag("resonance.sheet.row.custom-1"))
        rule.onNodeWithTag("resonance.sheet.row.custom-1").assertIsDisplayed()
        rule.onNodeWithTag("resonance.sheet.row.custom-1.edit").assertDoesNotExist()
        rule.onNodeWithTag("resonance.sheet.add").assertDoesNotExist()
    }

    @Test
    fun overlaySpellingControlInvokesCallback() {
        val calls = mutableListOf<dev.anima.resonance.music.Spelling>()
        val ui = state(status = TunerStatus.LISTENING).copy(
            presetRows = PresetSheetUi.toRows(BuiltInTunings.all, setOf("standard"), "standard"),
        )
        showOverlay(ui, onSpellingSelect = { calls.add(it) }, initialSection = dev.anima.resonance.presentation.SheetSection.CALIBRATION)
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
        showOverlay(ui, initialSection = dev.anima.resonance.presentation.SheetSection.MANAGE)
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