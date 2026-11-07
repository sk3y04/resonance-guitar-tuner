package dev.anima.resonance.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anima.resonance.audio.AndroidAudioInput
import dev.anima.resonance.audio.CaptureOrchestrator
import dev.anima.resonance.audio.CaptureState
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * THE real-device gate. The real microphone under the real audio clock —
 * the only evidence a device can give that the tuner actually works.
 *
 * Setup (done by ./scripts/test-device.sh before the run):
 *   adb shell pm grant dev.anima.resonance android.permission.RECORD_AUDIO
 * An unattended instrumented run cannot tap a permission dialog, so the
 * test ASSUMES the grant and skips (reports the exact command) otherwise.
 */
@RunWith(AndroidJUnit4::class)
class RealMicSmokeTest {

    @Test
    fun realMicrophoneFeedsThePipeline() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val granted = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        assumeTrue(
            "RECORD_AUDIO not granted — run: adb -s <serial> shell pm grant dev.anima.resonance android.permission.RECORD_AUDIO",
            granted,
        )

        val chunks = CopyOnWriteArrayList<FloatArray>()
        val input = AndroidAudioInput()
        val orchestrator = CaptureOrchestrator(
            input = input,
            onChunk = { chunk, _ -> chunks.add(chunk) },
            onState = { /* state transitions observed via orchestrator.state */ },
        )

        orchestrator.start()
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (orchestrator.state != CaptureState.CAPTURING &&
            SystemClock.elapsedRealtime() < deadline
        ) {
            Thread.sleep(50)
        }
        try {
            assertEquals(
                "real mic must reach CAPTURING within 5 s",
                CaptureState.CAPTURING,
                orchestrator.state,
            )

            // Ambient air always carries some sound; the reader thread
            // must deliver real PCM within 3 s of capture start.
            val pumpEnd = SystemClock.elapsedRealtime() + 3_000
            while (SystemClock.elapsedRealtime() < pumpEnd) Thread.sleep(20)
        } finally {
            orchestrator.stop()
        }

        assertTrue("expected PCM chunks from the mic, got 0", chunks.isNotEmpty())
        val maxRms = chunks.maxOf { c -> sqrt(c.sumOf { s -> s.toDouble() * s } / c.size) }
        assertTrue("ambient RMS must be > 0 (got $maxRms)", maxRms > 0.0)
    }
}
