package dev.anima.resonance.demo

import dev.anima.resonance.audio.AudioInput
import dev.anima.resonance.engine.TunerController
import dev.anima.resonance.music.Note
import dev.anima.resonance.persistence.InMemorySettingsRepository
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.persistence.Settings
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.TunerMode
import dev.anima.resonance.presentation.TunerStatus
import kotlinx.coroutines.runBlocking
import kotlin.math.PI
import kotlin.math.sin
import kotlin.system.exitProcess

/**
 * Resonance host demo — the FULL pipeline as an executable program:
 * synthetic mic → AppTuner (YIN + state machine) → TunerController
 * (permissions, presets, settings) → MainScreenUiState (what the UI shows).
 *
 * Deterministic: virtual clock, no device, no display, no SDK. Each scenario
 * asserts the same contracts the 167 host tests cover; a failure exits 1.
 * Run: ./gradlew :hostDemo:run   (or ./scripts/run-demo.sh)
 */

/** Deterministic mono sine "microphone" with device-loss injection. */
class SynthMic(
    startHz: Double? = null,
    override val sampleRate: Int = 48_000,
    private val chunkSize: Int = 1024,
) : AudioInput {
    var toneHz: Double? = startHz
    /** Number of chunks to report as lost (null) before resuming. */
    var dropNext = 0
    var opens = 0
    var closes = 0
    private var phase = 0.0

    override fun open(): Boolean {
        opens++
        return true
    }

    override fun readChunk(): FloatArray? {
        if (dropNext > 0) {
            dropNext--
            return null
        }
        val hz = toneHz ?: return FloatArray(chunkSize)
        val out = FloatArray(chunkSize)
        repeat(chunkSize) {
            out[it] = (0.3 * sin(phase)).toFloat()
            phase += 2.0 * PI * hz / sampleRate
            if (phase > 2.0 * PI) phase -= 2.0 * PI
        }
        return out
    }

    override fun close() {
        closes++
    }
}

private data class Outcome(val name: String, val pass: Boolean, val detail: String)

private val outcomes = mutableListOf<Outcome>()

private fun freq(note: String, octave: Int, concert: Double = 440.0): Double =
    Note(note, octave).frequency(concert)

private fun harness(
    mic: SynthMic,
    initial: Settings = Settings.DEFAULTS,
): TunerController {
    val controller = TunerController(mic, InMemorySettingsRepository(initial), PresetStore())
    runBlocking {
        controller.onPermissionResult(MicPermissionState.GRANTED)
        controller.startCapture()
    }
    return controller
}

private fun pump(controller: TunerController, frames: Int, stepMs: Long = 21, fromMs: Long = 0): Long {
    var t = fromMs
    repeat(frames) {
        t += stepMs
        controller.pump(t)
    }
    return t
}

private fun describe(ui: dev.anima.resonance.presentation.MainScreenUiState): String =
    "status=${ui.status} note=${ui.noteLabel}${ui.octave ?: ""} " +
        "freq=${ui.frequencyText ?: "-"} cents=${ui.centsText ?: "-"} " +
        "inTune=${ui.inTune} preset=${ui.presetName ?: "Chromatic"}"

/** [1] Chromatic mode detects a steady E2 and reports it in tune. */
private fun chromaticDetectsE2() {
    val controller = harness(SynthMic(freq("E", 2)))
    pump(controller, 14)
    val ui = controller.state()
    val ok = ui.status == TunerStatus.MEASURED &&
        ui.noteLabel == "E" && ui.octave == "2" && ui.inTune
    println("[1] chromatic — steady E2 tone")
    println("    " + describe(ui))
    outcomes += Outcome("chromatic detects E2 in tune", ok, describe(ui))
}

/** [2] Instrument mode: AUTO follows the played string (G3 → G3 in tune);
 *  locking the E2 6th string switches the target (not the measurement) to a
 *  huge deviation, and tapping it again returns to auto. */
private fun instrumentModeAutoThenLock() {
    val mic = SynthMic(freq("G", 3))
    val controller = harness(mic, Settings.DEFAULTS.copy(mode = TunerMode.INSTRUMENT, selectedPresetId = "standard"))
    pump(controller, 14)
    val auto = controller.state()
    controller.selectString(0) // lock the E2 6th string
    pump(controller, 14)
    val locked = controller.state()
    controller.selectString(0) // unlock → auto again
    pump(controller, 14)
    val unLocked = controller.state()
    val ok = auto.noteLabel == "G" && auto.octave == "3" && auto.inTune &&
        locked.noteLabel == "E" && locked.octave == "2" && !locked.inTune &&
        locked.selectedStringIndex == 0 &&
        unLocked.noteLabel == "G" && unLocked.inTune && unLocked.selectedStringIndex == null
    println("[2] instrument — G3 tone: auto, then locked to E2, then unlocked")
    println("    auto:     " + describe(auto))
    println("    locked:   " + describe(locked))
    println("    unlocked: " + describe(unLocked))
    outcomes += Outcome("instrument auto selection + manual lock + unlock", ok,
        "auto=${auto.noteLabel}${auto.octave} locked=${locked.noteLabel}${locked.octave} cents=${locked.centsText}")
}

/** [3] Chromatic mode measures the played note freely: G3 → G3. */
private fun chromaticMeasuresG3() {
    val controller = harness(SynthMic(freq("G", 3)))
    pump(controller, 14)
    val ui = controller.state()
    val ok = ui.status == TunerStatus.MEASURED &&
        ui.noteLabel == "G" && ui.octave == "3" && ui.inTune
    println("[3] chromatic — steady G3 tone")
    println("    " + describe(ui))
    outcomes += Outcome("chromatic measures G3 freely", ok, describe(ui))
}

/** [4] A4 = 432 Hz: the same tone is flat under 440, in tune after the
 *  persisted concert-pitch change. */
private fun concert432Retunes() {
    val controller = harness(SynthMic(432.0))
    pump(controller, 14)
    val before = controller.state()
    runBlocking { controller.setConcertPitch(432.0) }
    pump(controller, 14)
    val after = controller.state()
    val ok = before.centsText?.toIntOrNull()?.let { it < 0 } == true &&
        after.noteLabel == "A" && after.octave == "4" && after.inTune &&
        after.concertPitchHz == 432.0
    println("[4] concert pitch — 432 Hz tone, A4 changed 440 → 432")
    println("    before: " + describe(before))
    println("    after:  " + describe(after))
    outcomes += Outcome("A4=432 retunes the same tone in tune", ok,
        "before cents=${before.centsText}, after " + describe(after))
}

/** [5] Three consecutive device losses surface a capture error; the user
 *  retry reopens the device and clears it. */
private fun captureFailureRecovers() {
    val mic = SynthMic(freq("E", 2))
    val controller = harness(mic)
    mic.dropNext = 3
    pump(controller, 4)
    val failed = controller.state()
    mic.dropNext = 0
    runBlocking { controller.retryCapture() }
    pump(controller, 4)
    val recovered = controller.state()
    val ok = failed.captureError && !recovered.captureError && mic.opens >= 2
    println("[5] capture recovery — 3 lost chunks, then user retry")
    println("    after failure: captureError=${failed.captureError}")
    println("    after retry:   captureError=${recovered.captureError} deviceOpens=${mic.opens}")
    outcomes += Outcome("capture error surfaces and retry recovers", ok,
        "failed=${failed.captureError} recovered=${recovered.captureError} opens=${mic.opens}")
}

fun main() {
    println("=== Resonance host demo — deterministic synthetic mic, virtual clock ===")
    println("    (synthetic mic → AppTuner → TunerController → UI state)\n")
    chromaticDetectsE2()
    instrumentModeAutoThenLock()
    chromaticMeasuresG3()
    concert432Retunes()
    captureFailureRecovers()
    println()
    outcomes.forEach {
        println(if (it.pass) "PASS  ${it.name}" else "FAIL  ${it.name} — ${it.detail}")
    }
    val failed = outcomes.count { !it.pass }
    println("\n=== ${outcomes.size - failed}/${outcomes.size} scenarios passed ===")
    if (failed > 0) exitProcess(1)
}
