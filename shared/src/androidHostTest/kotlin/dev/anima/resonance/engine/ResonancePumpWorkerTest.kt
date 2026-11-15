package dev.anima.resonance.engine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for the Android lifecycle pump.
 *
 * Capture now stops in the background and restarts on foreground. The pump
 * worker must therefore survive stop → start; the previous `Thread`
 * subclass could only be started once and would throw
 * `IllegalThreadStateException` on the second start.
 */
class ResonancePumpWorkerTest {

    @Test
    fun startStopStartResumesWithoutRestartingADeadThread() {
        val pumped = CountDownLatch(2)
        val worker = ResonancePumpWorker({ pumped.countDown() }, sleepMs = 1)
        try {
            worker.startFeeding()
            assertTrue("first feeding period must pump", pumped.await(2, TimeUnit.SECONDS))
            worker.stopFeeding()
            worker.startFeeding()
            assertTrue("second feeding period must pump", pumped.await(2, TimeUnit.SECONDS))
        } finally {
            worker.shutdown()
        }
    }

    @Test
    fun shutdownIsIdempotentAndSafeWhenNeverStarted() {
        val worker = ResonancePumpWorker({}, sleepMs = 1)
        worker.shutdown()
        worker.shutdown()
        worker.stopFeeding()
    }
}