package dev.anima.resonance.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
    fun mainScreenShowsListeningHintBeforeStart() {
        val (controller, _) = controller(hz = 100.0)
        rule.setContent { MainScreen(controller) }
        rule.waitUntil(timeoutMillis = 5000) {
            rule.onAllNodes(hasText("Play a note near the microphone."))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("resonance.hint").assertExists()
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
            rule.onAllNodes(hasTestTag("resonance.sheet.row.drop-d"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
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