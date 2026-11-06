package dev.anima.resonance.engine

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.anima.resonance.audio.AndroidAudioInput
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.persistence.createContextDataStore
import kotlinx.coroutines.launch

/**
 * Android shell ViewModel. A THIN layer over the common
 * [TunerController] (all behavior is tested on the host in
 * TunerControllerTest / AppTunerTest): it provides the platform audio
 * backend, the Context-backed DataStore, and the pump thread that drives
 * the engine from the audio clock.
 *
 * Status: WRITTEN — COMPILE VERIFICATION BLOCKED (SDK platform /
 * licenses not accepted → android target auto-disabled). No device
 * testing has been performed.
 *
 * Permission policy (enforced by the Activity, not here): the mic is only
 * opened after RECORD_AUDIO is granted; permanent denial maps to
 * DENIED_PERMANENT, which the UI renders as the "open app settings" CTA
 * (never re-prompts).
 */
class ResonanceViewModel(application: Application) : AndroidViewModel(application) {

    val controller: TunerController by lazy {
        TunerController(
            input = AndroidAudioInput(),
            settingsRepo = dev.anima.resonance.persistence.DataStoreSettingsRepository(
                createContextDataStore(getApplication()),
            ),
            presets = PresetStore(),
        )
    }

    private val pumpThread = PumpWorker(controller)

    /** Starts capture (if permitted) and the pump thread. */
    fun start() {
        viewModelScope.launch {
            controller.startCapture()
            if (controller.captureState == dev.anima.resonance.audio.CaptureState.CAPTURING) {
                pumpThread.startFeeding()
            }
        }
    }

    /** Stops capture and the pump thread. */
    fun stop() {
        pumpThread.stopFeeding()
        viewModelScope.launch { controller.stopCapture() }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    /**
     * One dedicated thread pumps the engine on the audio clock. While
     * capturing, each poll blocks ~21 ms on the chunk read; the short sleep
     * prevents a spin when the orchestrator is not capturing.
     */
    private class PumpWorker(private val controller: TunerController) : Thread("resonance-pump") {
        @Volatile
        private var running = false

        fun startFeeding() {
            if (running) return
            running = true
            start()
        }

        fun stopFeeding() {
            running = false
            join(500)
        }

        override fun run() {
            while (running) {
                controller.pump(SystemClock.elapsedRealtime())
                try {
                    sleep(2)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }
}
