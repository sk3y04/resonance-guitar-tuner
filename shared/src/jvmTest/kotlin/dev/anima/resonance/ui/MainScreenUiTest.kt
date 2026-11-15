package dev.anima.resonance.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import dev.anima.resonance.audio.AudioInput
import dev.anima.resonance.engine.TunerController
import dev.anima.resonance.music.Note
import dev.anima.resonance.persistence.InMemorySettingsRepository
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.persistence.Settings
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.TunerMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The host screen against a REAL controller (fake audio, virtual
 * clock) — the full path from device chunks to rendered semantics.
 */
class MainScreenUiTest {

    @get:Rule
    val rule = createComposeRule()

    private class ToneInput(
        private val hz: Double,
        override val sampleRate: Int = 48000,
        private val chunkSize: Int = 1024,
    ) : AudioInput {
        private var phase = 0.0
        override fun open(): Boolean = true
        override fun readChunk(): FloatArray? {
            val out = FloatArray(chunkSize)
            repeat(chunkSize) {
                out[it] = (0.3 * sin(phase)).toFloat()
                phase += 2.0 * PI * hz / sampleRate
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
            return out
        }
        override fun close() {}
    }

    private fun freq(note: String, octave: Int, concert: Double = 440.0): Double =
        Note(note, octave).frequency(concert)

    private fun pump(controller: TunerController, frames: Int, stepMs: Long = 21, fromMs: Long = 0): Long {
        var t = fromMs
        repeat(frames) {
            t += stepMs
            controller.pump(t)
        }
        return t
    }

    private fun controller(
        initial: Settings = Settings.DEFAULTS,
        hz: Double? = null,
    ): Pair<TunerController, AudioInput> {
        val input = if (hz != null) ToneInput(hz) else SilenceInput()
        return TunerController(input, InMemorySettingsRepository(initial), PresetStore()) to input
    }

    private class SilenceInput : AudioInput {
        override val sampleRate = 48000
        override fun open(): Boolean = true
        override fun readChunk(): FloatArray = FloatArray(1024)
        override fun close() {}
    }

    @Test
    fun mainScreenShowsMeasuredNoteFromController() {
        val (controller, _) = controller(hz = freq("E", 2))
        runBlocking {
            controller.onPermissionResult(MicPermissionState.GRANTED)
            controller.startCapture()
        }
        rule.setContent { MainScreen(controller) }
        pump(controller, 14)
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasText("E"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("resonance.note").assertExists()
        rule.onNodeWithTag("resonance.cents").assertExists()
        assertEquals(
            "E",
            rule.onNodeWithTag("resonance.note").fetchSemanticsNode().config[SemanticsProperties.Text]
                ?.joinToString("") { it.text },
        )
    }

    @Test
    fun mainScreenDoesNotClaimListeningBeforeCaptureStarts() {
        val (controller, _) = controller(hz = 100.0)
        // Permission granted, but capture intentionally not started yet.
        controller.onPermissionResult(MicPermissionState.GRANTED)
        rule.setContent { MainScreen(controller) }
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasText("Microphone is off."))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("resonance.hint").assertExists()
        rule.onNodeWithTag("resonance.micStatus").assertExists()
    }

    @Test
    fun editorCreateFlowPersistsCustomPreset() {
        val repo = InMemorySettingsRepository()
        val controller = TunerController(SilenceInput(), repo, PresetStore())
        runBlocking {
            controller.onPermissionResult(MicPermissionState.GRANTED)
            controller.startCapture()
        }
        rule.setContent { MainScreen(controller) }
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.openSheet"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        // Configuration lives in the overlay opened by the labeled key.
        rule.onNodeWithTag("resonance.openSheet").performClick()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.sheet"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        // The overlay scrolls; the main instrument does not.
        rule.onNodeWithTag("resonance.overlay.scroll")
            .performScrollToNode(hasTestTag("resonance.sheet.add"))
        rule.onNodeWithTag("resonance.sheet.add").performClick()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.editor.name"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("resonance.overlay.scroll")
            .performScrollToNode(hasTestTag("resonance.editor.name"))
        rule.onNodeWithTag("resonance.editor.name").performTextInput("UI Created")
        // default first string is E2; three upward note cycles → G2 (E→F→F#→G)
        rule.onNodeWithTag("resonance.editor.string.0.note").performClick()
        rule.onNodeWithTag("resonance.editor.string.0.note").performClick()
        rule.onNodeWithTag("resonance.editor.string.0.note").performClick()
        rule.onNodeWithTag("resonance.overlay.scroll")
            .performScrollToNode(hasTestTag("resonance.editor.save"))
        rule.onNodeWithTag("resonance.editor.save").performClick()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.sheet.row.custom-1"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        runBlocking {
            val created = repo.customsSnapshot.single()
            assertEquals("UI Created", created.name)
            assertEquals(dev.anima.resonance.music.Note.fromLabel("G2"), created.strings.single().note)
        }
    }

    @Test
    fun deletingACustomTuningRequiresConfirmation() {
        val repo = InMemorySettingsRepository()
        val controller = TunerController(SilenceInput(), repo, PresetStore())
        runBlocking {
            controller.onPermissionResult(MicPermissionState.GRANTED)
            controller.startCapture()
            controller.createCustomPreset("Keep Me", listOf(Note.fromLabel("E2")))
        }
        rule.setContent { MainScreen(controller) }
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.openSheet"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("resonance.openSheet").performClick()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.sheet.row.custom-1.delete"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }

        rule.onNodeWithTag("resonance.overlay.scroll")
            .performScrollToNode(hasTestTag("resonance.sheet.row.custom-1.delete"))
        rule.onNodeWithTag("resonance.sheet.row.custom-1.delete").performClick()
        rule.onNodeWithTag("resonance.deleteDialog").assertExists()
        rule.onNodeWithTag("resonance.deleteDialog.cancel").performClick()
        runBlocking {
            assertTrue(
                repo.customsSnapshot.any { it.name == "Keep Me" },
                "cancel must keep the user's tuning",
            )
        }

        rule.onNodeWithTag("resonance.sheet.row.custom-1.delete").performClick()
        rule.onNodeWithTag("resonance.deleteDialog.confirm").performClick()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.deleteDialog"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        runBlocking {
            assertTrue(repo.customsSnapshot.isEmpty(), "confirm must delete exactly the chosen tuning")
        }
    }

    @Test
    fun editingAFlatTuningPreservesEveryStringAndItsSpelling() {
        val repo = InMemorySettingsRepository()
        val controller = TunerController(SilenceInput(), repo, PresetStore())
        runBlocking {
            controller.onPermissionResult(MicPermissionState.GRANTED)
            controller.startCapture()
            controller.createCustomPreset(
                "Flat Tuning",
                listOf(Note.fromLabel("Eb2"), Note.fromLabel("Ab2")),
            )
        }
        rule.setContent { MainScreen(controller) }
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.openSheet"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("resonance.openSheet").performClick()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.sheet.row.custom-1.edit"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("resonance.overlay.scroll")
            .performScrollToNode(hasTestTag("resonance.sheet.row.custom-1.edit"))
        rule.onNodeWithTag("resonance.sheet.row.custom-1.edit").performClick()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.editor.string.0.note"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("resonance.overlay.scroll")
            .performScrollToNode(hasTestTag("resonance.editor.string.0.note"))
        assertEquals(
            "Eb",
            rule.onNodeWithTag("resonance.editor.string.0.note")
                .fetchSemanticsNode().config[SemanticsProperties.Text]!!
                .joinToString("") { it.text },
            "the editor must not drop the flat accidental",
        )
        rule.onNodeWithTag("resonance.overlay.scroll")
            .performScrollToNode(hasTestTag("resonance.editor.save"))
        rule.onNodeWithTag("resonance.editor.save").performClick()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.editor")).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        runBlocking {
            val saved = repo.customsSnapshot.single()
            assertEquals(
                listOf(Note.fromLabel("Eb2"), Note.fromLabel("Ab2")),
                saved.strings.map { it.note },
                "editing without changes must be lossless",
            )
        }
    }

    @Test
    fun importPayloadTriggersSheetFeedback() {
        val repo = InMemorySettingsRepository()
        val controller = TunerController(SilenceInput(), repo, PresetStore())
        runBlocking {
            controller.onPermissionResult(MicPermissionState.GRANTED)
            controller.startCapture()
        }
        val payload = dev.anima.resonance.music.TuningPresetJson.encode(
            listOf(
                dev.anima.resonance.music.TuningPreset("p7-imp", "P7 Import", builtIn = false,
                    strings = listOf(dev.anima.resonance.music.TuningString("p7-imp-s1", Note.fromLabel("D2")))),
            ),
        )
        rule.setContent { MainScreen(controller, importPayload = payload, onImportConsumed = {}) }
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.openSheet"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("resonance.openSheet").performClick()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.sheet.importResult"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("resonance.overlay.scroll")
            .performScrollToNode(hasTestTag("resonance.sheet.importResult"))
        val text = rule.onNodeWithTag("resonance.sheet.importResult")
            .fetchSemanticsNode().config[SemanticsProperties.Text]!!
            .joinToString("") { it.text }
        assertTrue(text.contains("Imported 1"), "feedback must report the import: $text")
        runBlocking {
            assertTrue(repo.customsSnapshot.any { it.name == "P7 Import" })
        }
    }

    @Test
    fun presetSelectionFlowsThroughController() {
        val (controller, _) = controller(
            Settings.DEFAULTS.copy(mode = TunerMode.INSTRUMENT),
            hz = 100.0,
        )
        runBlocking {
            controller.onPermissionResult(MicPermissionState.GRANTED)
            controller.startCapture()
        }
        rule.setContent { MainScreen(controller) }
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.openSheet"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("resonance.openSheet").performClick()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasTestTag("resonance.sheet.row.drop-d"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("resonance.overlay.scroll")
            .performScrollToNode(hasTestTag("resonance.sheet.row.drop-d"))
        rule.onNodeWithTag("resonance.sheet.row.drop-d").performClick()
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasText("Drop D"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        assertEquals(
            "Drop D",
            rule.onNodeWithTag("resonance.preset").fetchSemanticsNode().config[SemanticsProperties.Text]
                ?.joinToString("") { it.text },
        )
    }
}