package dev.anima.resonance.engine

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Imported documents are untrusted. The Android shell must not read an
 * unbounded stream into memory before the codec's own validation runs.
 */
class ImportIoTest {

    @Test
    fun readsUpToTheBoundAndRejectsLargerPayloads() {
        val payload = "x".repeat(1024)
        assertEquals(payload, ResonanceViewModel.readBounded(ByteArrayInputStream(payload.encodeToByteArray()), 1024))
        assertNull(
            ResonanceViewModel.readBounded(
                ByteArrayInputStream(("x".repeat(1025)).encodeToByteArray()),
                1024,
            ),
            "one byte over the bound must be rejected, not truncated into valid-looking JSON",
        )
    }
}
