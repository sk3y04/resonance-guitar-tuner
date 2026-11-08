package dev.anima.resonance.engine

import dev.anima.resonance.audio.AudioInput
import dev.anima.resonance.music.BuiltInTunings
import dev.anima.resonance.music.Note
import dev.anima.resonance.persistence.InMemorySettingsRepository
import dev.anima.resonance.persistence.PresetStore
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.TunerMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Concurrency (JVM-only: real threads). The Android shell drives the
 * engine from TWO threads — the pump thread (controller.pump, ~47 fps) and
 * the main thread (settings, CRUD, UI state reads every frame). This test
 * reproduces that split: a pump thread hammers pump() with a tone while the
 * main thread runs a random settings/CRUD storm and reads state()
 * continuously.
 *
 * Contract: no exception may escape either thread, and every engine
 * invariant must hold afterwards (unique IDs, built-ins intact, engine
 * still locks a tone). Pre-fix, this split produced
 * ConcurrentModificationException / ArrayIndexOutOfBoundsException inside
 * the state machine's smoothing ring and the preset list.
 */
class AppTunerConcurrencyTest {

    private class ToneInput(private val hz: Double, override val sampleRate: Int = 48000) : AudioInput {
        private var phase = 0.0
        override fun open(): Boolean = true
        override fun readChunk(): FloatArray {
            val out = FloatArray(1024)
            repeat(1024) {
                out[it] = (0.3 * sin(phase)).toFloat()
                phase += 2.0 * PI * hz / sampleRate
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
            return out
        }
        override fun close() {}
    }

    @Test
    fun pumpAndSettingsStormsNeverCorruptTheEngine() {
        val rng = Random(0x03L)
        val input = ToneInput(Note("E", 2).frequency(440.0))
        val repo = InMemorySettingsRepository()
        val store = PresetStore()
        val controller = TunerController(input, repo, store)
        controller.onPermissionResult(MicPermissionState.GRANTED)
        runBlocking { controller.startCapture() }

        val pumpError = AtomicReference<Throwable?>(null)
        val mainError = AtomicReference<Throwable?>(null)
        val stop = AtomicBoolean(false)
        val pumpStarted = CountDownLatch(1)

        val pump = Thread({
            pumpStarted.countDown()
            var t = 0L
            // Deliberate spin (no sleep): the real pump is paced by the mic
            // read (~47 fps), which makes this interleaving rare but
            // inevitable; spinning just accelerates time to the same race.
            while (!stop.get()) {
                try {
                    t += 21
                    controller.pump(t)
                } catch (e: Throwable) {
                    pumpError.compareAndSet(null, e)
                    break
                }
            }
        }, "qn03-pump")
        pump.start()
        assertTrue("pump must start", pumpStarted.await(5, TimeUnit.SECONDS))

        val letters = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val builtInIds = BuiltInTunings.all.map { it.id } // immutable — safe to read
        var customs = emptyList<dev.anima.resonance.music.TuningPreset>()
        val deadline = System.currentTimeMillis() + 10_000
        try {
            while (System.currentTimeMillis() < deadline && mainError.get() == null) {
                when (rng.nextInt(12)) {
                    0 -> runBlocking { controller.setConcertPitch(400.0 + rng.nextDouble() * 80.0) }
                    1 -> runBlocking { controller.stepConcertPitch(rng.nextBoolean()) }
                    2 -> runBlocking { controller.setMode(if (rng.nextBoolean()) TunerMode.CHROMATIC else TunerMode.INSTRUMENT) }
                    3 -> runBlocking { controller.setPreset(builtInIds[rng.nextInt(builtInIds.size)]) }
                    4 -> controller.selectString(rng.nextInt(7))
                    5 -> runBlocking {
                        controller.createCustomPreset("p" + rng.nextLong(1_000_000).toString(36),
                            List(1 + rng.nextInt(6)) { Note.fromLabel("${letters[rng.nextInt(letters.size)]}${2 + rng.nextInt(4)}") })
                        customs += store.customs().last() // main-thread only: the pump never touches the store
                    }
                    6 -> if (customs.isNotEmpty()) runBlocking {
                        val id = customs[rng.nextInt(customs.size)].id
                        try {
                            controller.editCustomPreset(id, "e",
                                List(1 + rng.nextInt(6)) { Note.fromLabel("${letters[rng.nextInt(letters.size)]}${2 + rng.nextInt(4)}") })
                            val edited = store.byId(id) ?: throw AssertionError("edited preset missing: $id")
                            customs = customs.map { if (it.id == edited.id) edited else it }
                        } catch (e: IllegalStateException) {
                            throw e // a real product bug: must be IAE, never anything else
                        } catch (e: IllegalArgumentException) { /* deleted under us */ }
                    }
                    7 -> if (customs.isNotEmpty()) runBlocking {
                        val id = customs[rng.nextInt(customs.size)].id
                        if (controller.deleteCustomPreset(id)) customs = customs.filter { it.id != id }
                    }
                    8 -> runBlocking {
                        val id = builtInIds[rng.nextInt(builtInIds.size)]
                        try {
                            controller.toggleFavorite(id)
                        } catch (e: IllegalArgumentException) { throw e }
                    }
                    9 -> runBlocking { controller.importCustomPresets("garbage ${rng.nextInt(100)}") }
                    10 -> { /* continuous UI-state read, like Compose recomposition */
                        val s = controller.state()
                        assertTrue("torn/out-of-band concert read: ${s.concertPitchHz}",
                            s.concertPitchHz in 400.0..480.0)
                    }
                    else -> Thread.sleep(1)
                }
            }
        } catch (e: Throwable) {
            mainError.set(e)
        }
        stop.set(true)
        pump.join(5000)

        assertTrue("pump thread threw: ${pumpError.get()}", pumpError.get() == null)
        assertTrue("main thread threw: ${mainError.get()}", mainError.get() == null)

        // Engine invariants after the storm (pump stopped → single-threaded reads).
        val view = store.all()
        val ids = view.map { it.id }
        assertEquals("preset IDs must stay unique", ids.toSet().size, ids.size)
        assertTrue("built-ins intact", builtInIds.all { it in ids.toSet() })
        assertEquals("built-ins first, in order", BuiltInTunings.all.map { it.id }, view.take(builtInIds.size).map { it.id })
        val settings = runBlocking {
            controller.setConcertPitch(440.0)
            controller.setMode(TunerMode.CHROMATIC)
            repo.get()
        }
        assertEquals(440.0, settings.concertPitchHz, 1e-9)
        // And the engine still works: a tone locks.
        var t = 0L
        var note: String? = null
        var inTune = false
        repeat(80) {
            t += 21
            controller.pump(t)
            val s = controller.state()
            if (s.noteLabel != null) {
                note = s.noteLabel + (s.octave ?: "")
                inTune = s.inTune
            }
        }
        assertEquals("E2 must lock after the storm", "E2", note)
        assertTrue("E2 must lock in tune", inTune)
        runBlocking { controller.stopCapture() }
    }
}
