package dev.anima.resonance.engine

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.anima.resonance.audio.AndroidAudioInput
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.persistence.createContextDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android shell ViewModel. A THIN layer over the common
 * [TunerController] (all behavior is tested on the host in
 * TunerControllerTest / AppTunerTest): it provides the platform audio
 * backend, the Context-backed DataStore, and the pump thread that drives
 * the engine from the audio clock.
 *
 * Status: COMPILE-VERIFIED (2026-11-06, first real Android
 * build — assembleDebug + lint green; picker wiring included). No
 * device testing has been performed.
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

    /**
     * Pending tuning-file payload from the import picker
     * (null = nothing pending). The common [MainScreen] handles it once
     * and calls [consumeImport]; all import semantics (validation,
     * dedupe, feedback) live in the tested common code.
     */
    private val _importPayload = MutableStateFlow<String?>(null)
    val importPayload: kotlinx.coroutines.flow.StateFlow<String?> = _importPayload.asStateFlow()

    /** Reads the picked document and stages it as [importPayload]. */
    fun onImportUri(uri: Uri) {
        viewModelScope.launch {
            val text = try {
                getApplication<Application>()
                    .contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().decodeToString() }
            } catch (e: Exception) {
                null
            }
            if (text != null) _importPayload.value = text
            // Unreadable documents are dropped silently here; malformed
            // CONTENT still flows through the codec and shows feedback.
        }
    }

    /** Signals that the staged payload was handled (clears it). */
    fun consumeImport() {
        _importPayload.value = null
    }

    /** Writes all custom tunings to the destination document. */
    fun onExportUri(uri: Uri) {
        viewModelScope.launch {
            getApplication<Application>()
                .contentResolver.openOutputStream(uri)
                ?.use { it.write(controller.exportCustomPresets().encodeToByteArray()) }
        }
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
