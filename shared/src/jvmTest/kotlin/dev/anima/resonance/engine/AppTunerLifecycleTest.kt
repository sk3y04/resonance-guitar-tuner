package dev.anima.resonance.engine

import dev.anima.resonance.audio.AudioInput
import dev.anima.resonance.music.TuningPreset
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.persistence.Settings
import dev.anima.resonance.persistence.SettingsRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A start that is suspended on persisted-settings I/O when the shell
 * stops must not open the microphone afterwards. The ViewModel cancels its
 * start job, and the engine's capture epoch is the second line of defense
 * (any shell that calls the controller directly gets the same guarantee).
 */
class AppTunerLifecycleTest {

    private class CountingInput : AudioInput {
        var opens = 0
        var closes = 0
        override val sampleRate = 48000
        override fun open(): Boolean {
            opens++
            return true
        }
        override fun readChunk(): FloatArray = FloatArray(1024)
        override fun close() {
            closes++
        }
    }

    private class BlockingRepo : SettingsRepository {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        private var current = Settings.DEFAULTS

        override suspend fun get(): Settings {
            entered.countDown()
            release.await()
            return current
        }

        override suspend fun update(transform: (Settings) -> Settings): Settings {
            current = transform(current)
            return current
        }

        override suspend fun loadCustomPresets(): List<TuningPreset> = emptyList()
    }

    @Test
    fun stopDuringSettingsLoadDoesNotOpenTheMicrophone() {
        val input = CountingInput()
        val repo = BlockingRepo()
        val engine = AppTuner(input, repo, PresetStore())
        val starting = Thread { runBlocking { engine.start() } }
        starting.start()
        try {
            assertTrue(repo.entered.await(2, TimeUnit.SECONDS), "start must reach settings I/O")
            engine.stop()
            assertFalse(engine.capturingUi)
        } finally {
            repo.release.countDown()
        }
        starting.join(2_000)
        assertEquals(0, input.opens, "a stopped start must never open the mic")
        assertFalse(engine.capturingUi)

        // A subsequent deliberate start (foreground) must still work.
        runBlocking { engine.start() }
        assertEquals(1, input.opens)
        assertTrue(engine.capturingUi)
        engine.stop()
        assertFalse(engine.capturingUi)
    }
}