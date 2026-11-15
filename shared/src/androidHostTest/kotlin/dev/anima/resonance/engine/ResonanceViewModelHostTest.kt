package dev.anima.resonance.engine

import android.net.Uri
import android.os.Looper
import dev.anima.resonance.music.Note
import dev.anima.resonance.music.TuningPresetJson
import dev.anima.resonance.music.TuningString
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Android bridge behaviorally verified on the JVM (Robolectric),
 * without a device. Covers the two SAF seams: import staging
 * (URI -> payload -> consume) and export writing (URI -> encoded JSON),
 * both through a real ContentResolver + real files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResonanceViewModelHostTest {

    @org.junit.Before
    fun resetStoreSingleton() {
        // Robolectric apps (and filesDirs) are per-test — drop the
        // per-process DataStore singleton so each test sees its own dir.
        dev.anima.resonance.persistence.resetContextDataStoreForTesting()
    }

    private val app get() = RuntimeEnvironment.getApplication()
    private val mainLooper get() = shadowOf(Looper.getMainLooper())

    /** Advances main-looper coroutines (viewModelScope) until [cond] holds. */
    private fun await(cond: () -> Boolean, what: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!cond()) {
            mainLooper.idle()
            if (System.currentTimeMillis() > deadline) fail("timed out waiting: $what")
        }
    }

    @Test
    fun importUriStagesPayloadAndConsumeClearsIt() {
        // Payload produced by the REAL codec — never hand-written JSON.
        val preset = dev.anima.resonance.music.TuningPreset(
            id = "p7-robo",
            name = "Robo Import",
            strings = listOf(TuningString("s0", Note("A", 1))),
        )
        val file = File(app.filesDir, "import.json")
        file.writeText(TuningPresetJson.encode(listOf(preset)))

        val vm = TestableViewModel(app)
        try {
            vm.onImportUri(Uri.fromFile(file))
            await({ vm.importPayload.value != null }, "payload staged from URI")

            val result = runBlocking(Dispatchers.IO) { vm.controller.importCustomPresets(vm.importPayload.value!!) }
            assertEquals(1, result.importedCount)
            assertEquals(null, result.error)

            vm.consumeImport()
            mainLooper.idle()
            assertNull("consumeImport must clear the staged payload", vm.importPayload.value)
        } finally {
            vm.clearForTest()
        }
    }

    @Test
    fun exportUriWritesCodecEncodedCustomPresets() {
        val file = File(app.filesDir, "export.json")
        val vm = TestableViewModel(app)
        try {
            runBlocking(Dispatchers.IO) {
                vm.controller.createCustomPreset("Robo Export", listOf(Note("E", 1), Note("A", 2)))
            }

            vm.onExportUri(Uri.fromFile(file))
            await({ file.exists() && file.length() > 0 }, "export file written")

            val decoded = TuningPresetJson.decode(file.readText())
            assertTrue(
                "export must round-trip the custom preset, got: $decoded",
                decoded.any { it.name == "Robo Export" && it.strings.size == 2 },
            )
        } finally {
            vm.clearForTest()
        }
    }

    @Test
    fun customTuningFavoriteAndSelectionSurviveAViewModelRecreation() {
        val first = TestableViewModel(app)
        try {
            runBlocking(Dispatchers.IO) {
                first.controller.onPermissionResult(dev.anima.resonance.presentation.MicPermissionState.GRANTED)
                first.controller.startCapture()
                val preset = first.controller.createCustomPreset(
                    "Robo Persisted",
                    listOf(Note.fromLabel("D2"), Note.fromLabel("A2"), Note.fromLabel("D3")),
                )
                first.controller.toggleFavorite(preset.id)
                first.controller.setMode(dev.anima.resonance.presentation.TunerMode.INSTRUMENT)
                first.controller.setPreset(preset.id)
                first.controller.stopCapture()
            }
        } finally {
            first.stop()
            first.clearForTest()
        }

        val second = TestableViewModel(app)
        try {
            runBlocking(Dispatchers.IO) {
                second.controller.onPermissionResult(dev.anima.resonance.presentation.MicPermissionState.GRANTED)
                second.controller.startCapture()
            }
            val ui = second.controller.state()
            val restored = ui.presetRows.single { it.name == "Robo Persisted" }
            assertTrue("favorite survives a real DataStore restart", restored.favorite)
            assertEquals("Robo Persisted", ui.presetName)
            assertEquals(listOf("D2", "A2", "D3"), ui.targetLabels)
        } finally {
            second.stop()
            second.clearForTest()
        }
    }

    @Test
    fun stopAndClearAreSafeWithoutCapture() {
        // Smoke: the lifecycle path used by Activity.onDestroy must not
        // throw when capture never started (Robolectric has no real mic).
        val vm = TestableViewModel(app)
        vm.stop()
        vm.clearForTest() // subclass access to the protected onCleared()
    }

    /** Exposes the protected onCleared() for the lifecycle smoke test. */
    private class TestableViewModel(app: android.app.Application) : ResonanceViewModel(app) {
        fun clearForTest() = onCleared()
    }
}
