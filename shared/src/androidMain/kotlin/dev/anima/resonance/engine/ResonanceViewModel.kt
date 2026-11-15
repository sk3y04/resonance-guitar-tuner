package dev.anima.resonance.engine

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.anima.resonance.audio.AndroidAudioInput
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.persistence.createContextDataStore
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android shell ViewModel. A THIN layer over the common [TunerController]
 * (all behavior is tested on the host in TunerControllerTest / AppTunerTest):
 * it provides the platform audio backend, the Context-backed DataStore, and
 * the pump thread that drives the engine from the audio clock.
 *
 * The pump is a single long-lived daemon thread that parks while capture is
 * stopped. This is deliberate: the previous design created a `Thread` per
 * start, which cannot be restarted after `stop()` — now that the Activity
 * actually stops capture in the background, a foreground return would have
 * crashed with `IllegalThreadStateException`.
 *
 * Permission policy (enforced by the Activity, not here): the mic is opened
 * only after RECORD_AUDIO is granted.
 */
open class ResonanceViewModel(application: Application) : AndroidViewModel(application) {

    val controller: TunerController by lazy {
        TunerController(
            input = AndroidAudioInput(),
            settingsRepo = dev.anima.resonance.persistence.DataStoreSettingsRepository(
                createContextDataStore(getApplication()),
            ),
            presets = PresetStore(),
        )
    }

    private val pumpThread = ResonancePumpWorker(
        pump = { controller.pump(SystemClock.elapsedRealtime()) },
    )

    private var startJob: Job? = null
    private var loadJob: Job? = null

    init {
        // Restore settings/tunings even before (or without) microphone
        // permission. Capture itself remains gated by the Activity.
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                controller.load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Unreadable storage must not crash the first screen; the
                // UI keeps safe defaults and the user can still retry later.
            }
        }
    }

    /**
     * Starts capture (if permitted) and feeds the pump thread. The job is
     * tracked so a quick backgrounding can cancel a start that is still
     * suspended on persisted-settings I/O before it opens the microphone.
     */
    fun start() {
        startJob?.cancel()
        startJob = viewModelScope.launch {
            try {
                controller.startCapture()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A settings read/open failure leaves capture closed; the
                // common UI shows the recovery surface and a retry action.
                return@launch
            }
            if (controller.captureState == dev.anima.resonance.audio.CaptureState.CAPTURING) {
                pumpThread.startFeeding()
            }
        }
    }

    /** Stops capture and parks the pump thread (thread itself is reusable). */
    fun stop() {
        startJob?.cancel()
        startJob = null
        pumpThread.stopFeeding()
        controller.stopCapture()
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
            // File I/O and the untrusted size cap stay off the main thread.
            val text = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>()
                        .contentResolver.openInputStream(uri)
                        ?.use { readBounded(it, MAX_IMPORT_BYTES) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (text != null) _importPayload.value = text
            // Unreadable/oversized documents are dropped here; malformed
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
            val payload = controller.exportCustomPresets().encodeToByteArray()
            withContext(Dispatchers.IO) {
                getApplication<Application>()
                    .contentResolver.openOutputStream(uri)
                    ?.use { it.write(payload) }
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        startJob?.cancel()
        pumpThread.shutdown()
        controller.stopCapture()
        super.onCleared()
    }

    internal companion object {
        /** Untrusted document bound; the codec's own payload cap is lower. */
        const val MAX_IMPORT_BYTES = 1_048_576

        internal fun readBounded(input: InputStream, maxBytes: Int): String? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) return null
                out.write(buffer, 0, read)
            }
            return out.toByteArray().decodeToString()
        }
    }
}

/**
 * One dedicated daemon thread pumps the engine on the audio clock while
 * [startFeeding] is active. It is created once and parks on a monitor when
 * idle — callable from any thread, restartable, and shut down with
 * [shutdown]. While capturing, each poll blocks on the chunk read; the short
 * sleep prevents a spin when the orchestrator is not capturing.
 *
 * Extracted and `internal` so the start→stop→start lifecycle is host-tested
 * without a microphone.
 */
internal class ResonancePumpWorker(
    private val pump: () -> Unit,
    private val sleepMs: Long = 2,
) : Thread("resonance-pump") {

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val monitor = Object()

    @Volatile
    private var running = false

    @Volatile
    private var stopped = false

    init {
        isDaemon = true
        start()
    }

    fun startFeeding() {
        synchronized(monitor) {
            if (stopped) return
            running = true
            monitor.notifyAll()
        }
    }

    fun stopFeeding() {
        running = false
    }

    fun shutdown() {
        synchronized(monitor) {
            running = false
            stopped = true
            monitor.notifyAll()
        }
    }

    override fun run() {
        while (true) {
            synchronized(monitor) {
                while (!running && !stopped) {
                    try {
                        monitor.wait()
                    } catch (e: InterruptedException) {
                        return
                    }
                }
                if (stopped) return
            }
            while (running && !stopped) {
                pump()
                try {
                    sleep(sleepMs)
                } catch (e: InterruptedException) {
                    return
                }
            }
        }
    }
}