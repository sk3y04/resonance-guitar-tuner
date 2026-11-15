package dev.anima.resonance.engine

import dev.anima.resonance.audio.AudioInput
import dev.anima.resonance.music.Note
import dev.anima.resonance.persistence.InMemorySettingsRepository
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.persistence.Settings
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.presentation.TunerStatus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The common controller between the engine and the screen —
 * permission gating, error surfacing, and the mapping to [MainScreenUiState].
 * Deterministic: fake audio + virtual clock, no threads.
 */
class TunerControllerTest {

    private class SilenceInput(
        override val sampleRate: Int = 48000,
        private val chunkSize: Int = 1024,
    ) : AudioInput {
        var opens = 0
        var closes = 0
        var openOk = true
        var dropNext = 0
        var toneHz: Double? = null
        private var phase = 0.0

        override fun open(): Boolean {
            opens++
            return openOk
        }

        override fun readChunk(): FloatArray? {
            if (dropNext > 0) {
                dropNext--
                return null
            }
            val hz = toneHz ?: return FloatArray(chunkSize)
            val out = FloatArray(chunkSize)
            repeat(chunkSize) {
                out[it] = (0.3 * kotlin.math.sin(phase)).toFloat()
                phase += 2.0 * kotlin.math.PI * hz / sampleRate
                if (phase > 2.0 * kotlin.math.PI) phase -= 2.0 * kotlin.math.PI
            }
            return out
        }

        override fun close() {
            closes++
        }
    }

    private fun freq(note: String, octave: Int, concert: Double = 440.0): Double =
        dev.anima.resonance.music.Note(note, octave).frequency(concert)

    private fun pump(controller: TunerController, frames: Int, stepMs: Long = 21, fromMs: Long = 0): Long {
        var t = fromMs
        repeat(frames) {
            t += stepMs
            controller.pump(t)
        }
        return t
    }

    private fun harness(initial: Settings = Settings.DEFAULTS): Pair<TunerController, SilenceInput> {
        val input = SilenceInput()
        val controller = TunerController(input, InMemorySettingsRepository(initial), PresetStore())
        return controller to input
    }

    @Test
    fun permissionGatesStart() = runBlocking {
        val (controller, input) = harness()
        controller.onPermissionResult(MicPermissionState.DENIED)
        controller.startCapture()
        assertEquals(0, input.opens, "denied permission must not open the mic")
        assertEquals(MicPermissionState.DENIED, controller.state().permission)
        assertTrue(controller.state().permissionCtaVisible || controller.state().permission == MicPermissionState.DENIED)

        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
        assertEquals(1, input.opens)
    }

    @Test
    fun stateReflectsMeasuredReading() = runBlocking {
        val (controller, input) = harness()
        input.toneHz = freq("E", 2)
        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
        pump(controller, 12)
        val ui = controller.state()
        assertEquals(TunerStatus.MEASURED, ui.status)
        assertEquals("E", ui.noteLabel)
        assertEquals("2", ui.octave)
        assertTrue(ui.inTune)
        assertEquals(0.0, ui.meterPosition, 0.01)
        assertFalse(ui.captureError)
        assertEquals(TunerMode.CHROMATIC, ui.mode)
    }

    @Test
    fun modeToggleUpdatesStateAndTargets() = runBlocking {
        val (controller, _) = harness()
        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
        controller.setMode(TunerMode.INSTRUMENT)
        val ui = controller.state()
        assertEquals(TunerMode.INSTRUMENT, ui.mode)
        assertEquals("Standard", ui.presetName)
        assertEquals(6, ui.targetLabels.size, "guitar preset shows six strings")
        assertEquals("E2", ui.targetLabels.first())
        assertEquals("E4", ui.targetLabels.last())
    }

    @Test
    fun stepConcertPitchPersistsAndClamps() = runBlocking {
        val (controller, _) = harness()
        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
        controller.stepConcertPitch(up = false)
        assertEquals(439.9, controller.state().concertPitchHz, 1e-9)
        // Drive to the lower bound: repeated down-steps must clamp at 400.0.
        repeat(400) { controller.stepConcertPitch(up = false) }
        assertEquals(400.0, controller.state().concertPitchHz, 1e-9)
        controller.stepConcertPitch(up = false)
        assertEquals(400.0, controller.state().concertPitchHz, 1e-9, "400 Hz is the floor")
    }

    @Test
    fun coarseConcertStepPersistsAndClamps() = runBlocking {
        val (controller, _) = harness()
        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
        repeat(8) { controller.stepConcertPitch(up = false, stepHz = 1.0) }
        assertEquals(432.0, controller.state().concertPitchHz, 1e-9, "eight coarse taps reach 432 Hz")
        repeat(100) { controller.stepConcertPitch(up = false, stepHz = 1.0) }
        assertEquals(400.0, controller.state().concertPitchHz, 1e-9)
    }

    @Test
    fun createCustomPresetAppearsInUiStateAndPersists() = runBlocking {
        val repo = InMemorySettingsRepository()
        val controller = TunerController(SilenceInput(), repo, PresetStore())
        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
        val preset = controller.createCustomPreset("UI Custom", listOf(Note.fromLabel("E2"), Note.fromLabel("A2")))
        assertTrue(preset.id in controller.state().presetRows.map { it.id }, "the sheet must list the new custom")
        assertEquals(1, repo.customsSnapshot.size, "customs must persist through the repository")
    }

    @Test
    fun relaunchRestoresCustomTuningsFavoritesAndSelection() = runBlocking {
        val repo = InMemorySettingsRepository()
        val first = TunerController(SilenceInput(), repo, PresetStore())
        first.onPermissionResult(MicPermissionState.GRANTED)
        first.startCapture()
        val preset = first.createCustomPreset(
            "Persisted Open D",
            listOf(Note.fromLabel("D2"), Note.fromLabel("A2"), Note.fromLabel("D3")),
        )
        first.toggleFavorite(preset.id)
        first.setMode(TunerMode.INSTRUMENT)
        first.setPreset(preset.id)
        first.stopCapture()

        // A new process: fresh store, same repository, no shared objects.
        val second = TunerController(SilenceInput(), repo, PresetStore())
        second.onPermissionResult(MicPermissionState.GRANTED)
        second.startCapture()
        val ui = second.state()
        assertEquals("Persisted Open D", ui.presetName, "selected custom tuning must restore")
        assertEquals(listOf("D2", "A2", "D3"), ui.targetLabels, "its targets must restore")
        val row = ui.presetRows.single { it.id == preset.id }
        assertTrue(row.favorite, "the favorite star must survive relaunch")
        assertEquals(TunerMode.INSTRUMENT, ui.mode)
    }

    @Test
    fun editingTheSelectedTuningRetargetsTheTunerAndClearsAStaleLock() = runBlocking {
        val repo = InMemorySettingsRepository()
        val controller = TunerController(SilenceInput(), repo, PresetStore())
        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
        val preset = controller.createCustomPreset(
            "Editable",
            listOf(Note.fromLabel("E2"), Note.fromLabel("A2")),
        )
        controller.setMode(TunerMode.INSTRUMENT)
        controller.setPreset(preset.id)
        controller.selectString(1)
        assertEquals(1, controller.state().selectedStringIndex)
        assertEquals(listOf("E2", "A2"), controller.state().targetLabels)

        controller.editCustomPreset(
            preset.id,
            "Editable",
            listOf(Note.fromLabel("D2"), Note.fromLabel("G2"), Note.fromLabel("D3")),
        )
        val ui = controller.state()
        assertEquals(
            listOf("D2", "G2", "D3"),
            ui.targetLabels,
            "the live target set must follow the edited preset",
        )
        assertNull(ui.selectedStringIndex, "a positional lock is meaningless after a retune")
        assertNull(ui.activeStringIndex)
    }

    @Test
    fun deleteSelectedCustomFallsBackToStandardInUiState() = runBlocking {
        val repo = InMemorySettingsRepository()
        val controller = TunerController(SilenceInput(), repo, PresetStore())
        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
        val preset = controller.createCustomPreset("Gone", listOf(Note.fromLabel("E2")))
        controller.setPreset(preset.id)
        controller.deleteCustomPreset(preset.id)
        assertEquals("standard", repo.get().selectedPresetId)
    }

    @Test
    fun setAccidentalSpellingPersistsAndDrivesUiState() = runBlocking {
        val repo = InMemorySettingsRepository()
        val controller = TunerController(SilenceInput(), repo, PresetStore())
        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
        controller.setAccidentalSpelling(dev.anima.resonance.music.Spelling.FLAT)
        assertEquals(dev.anima.resonance.music.Spelling.FLAT, repo.get().accidentalSpelling)
        assertEquals(dev.anima.resonance.music.Spelling.FLAT, controller.state().accidentalSpelling)
        controller.setAccidentalSpelling(dev.anima.resonance.music.Spelling.NATURAL)
        assertEquals(dev.anima.resonance.music.Spelling.NATURAL, repo.get().accidentalSpelling)
    }

    @Test
    fun autoModeShowsNoSelectionUntilLocked() = runBlocking {
        val (controller, _) = harness(
            Settings.DEFAULTS.copy(mode = TunerMode.INSTRUMENT, selectedPresetId = "standard"),
        )
        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
        assertNull(
            controller.state().selectedStringIndex,
            "auto mode: no string is selected until the user locks one",
        )
        controller.selectString(3)
        assertEquals(3, controller.state().selectedStringIndex)
        controller.selectString(3) // tap again → release the lock
        assertNull(controller.state().selectedStringIndex)
    }

    @Test
    fun presetAndStringSelectionFlow() = runBlocking {
        val (controller, _) = harness()
        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
        controller.setMode(TunerMode.INSTRUMENT)
        controller.setPreset("drop-d")
        var ui = controller.state()
        assertEquals("Drop D", ui.presetName)
        assertEquals("D2", ui.targetLabels.first())
        controller.selectString(3)
        ui = controller.state()
        assertEquals(3, ui.selectedStringIndex)
    }

    @Test
    fun captureErrorSurfacesAndRetries() = runBlocking {
        val (controller, input) = harness()
        controller.onPermissionResult(MicPermissionState.GRANTED)
        input.dropNext = 3
        controller.startCapture()
        pump(controller, 4)
        assertTrue(controller.state().captureError, "exhausted recovery must surface as error")

        input.dropNext = 0
        controller.retryCapture()
        assertFalse(controller.state().captureError)
        assertTrue(input.opens >= 2, "retry must reopen the device")
    }

    @Test
    fun loadRestoresSettingsAndLibraryWithoutOpeningTheMicrophone() = runBlocking {
        val input = SilenceInput()
        val repo = InMemorySettingsRepository(
            Settings.DEFAULTS.copy(
                concertPitchHz = 432.0,
                mode = TunerMode.INSTRUMENT,
                selectedPresetId = "drop-d",
                accidentalSpelling = dev.anima.resonance.music.Spelling.FLAT,
            ),
        )
        val controller = TunerController(input, repo, PresetStore())
        // No permission result, no startCapture — exactly the permanently
        // denied first screen.
        controller.load()
        val ui = controller.state()
        assertEquals(432.0, ui.concertPitchHz, 1e-9)
        assertEquals(TunerMode.INSTRUMENT, ui.mode)
        assertEquals("Drop D", ui.presetName)
        assertEquals(listOf("D2", "A2", "D3", "G3", "B3", "E4"), ui.targetLabels)
        assertEquals(dev.anima.resonance.music.Spelling.FLAT, ui.accidentalSpelling)
        assertFalse(ui.captureActive)
        assertEquals(0, input.opens, "loading settings must never touch the mic")
    }

    @Test
    fun captureActiveAndMicCopyTrackTheRealEngineState() = runBlocking {
        val (controller, _) = harness()
        controller.onPermissionResult(MicPermissionState.GRANTED)
        var ui = controller.state()
        assertFalse(ui.captureActive)
        assertEquals("Microphone off", ui.micStatusText)
        assertTrue(ui.statusHint!!.contains("off", ignoreCase = true))

        controller.startCapture()
        ui = controller.state()
        assertTrue(ui.captureActive)
        assertEquals("Listening", ui.micStatusText)

        controller.stopCapture()
        ui = controller.state()
        assertFalse(ui.captureActive)
        assertEquals("Microphone off", ui.micStatusText)
    }

    @Test
    fun favoriteToggleFlowsThroughControllerAndPersists() = runBlocking {
        val repo = InMemorySettingsRepository()
        val controller = TunerController(SilenceInput(), repo, PresetStore())
        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
        assertFalse(controller.state().presetRows.first { it.id == "standard" }.favorite)
        assertEquals(true, controller.toggleFavorite("standard"))
        assertTrue(controller.state().presetRows.first { it.id == "standard" }.favorite)
        assertTrue(repo.get().favorites.contains("standard"), "favorites must persist")
        assertEquals(false, controller.toggleFavorite("standard"))
        assertFalse(controller.state().presetRows.first { it.id == "standard" }.favorite)
    }

    @Test
    fun stopClearsTheScreen() = runBlocking {
        val (controller, input) = harness()
        input.toneHz = freq("E", 2)
        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
        pump(controller, 8)
        assertEquals(TunerStatus.MEASURED, controller.state().status)
        controller.stopCapture()
        assertEquals(TunerStatus.IDLE, controller.state().status)
        assertTrue(input.closes >= 1)
    }
}
