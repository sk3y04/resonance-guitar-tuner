package dev.anima.resonance.presentation

import dev.anima.resonance.music.Note
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The no-signal presentation must be ONE short, readable primary message
 * (not a long paragraph repeated across the meter/readout), and must never
 * fabricate a live reading. The needle is hidden when there is no reliable
 * pitch (see AnalogMeterGeometryTest). These pin the concise-message contract.
 */
class NoSignalPresentationTest {

    private fun snapshot(
        status: TunerStatus,
        note: Note? = null,
        freq: Double = 0.0,
        cents: Int = 0,
    ) = TunerSnapshot(
        reading = TunerReading(status, note, freq, cents, PitchDirection.FLAT, false, 1.0),
        rms = 0.3,
        clipping = status == TunerStatus.CLIPPING,
        sampleRate = 48000,
    )

    private fun state(status: TunerStatus) = MainScreenUi.toUiState(
        snapshot = snapshot(status),
        permission = MicPermissionState.GRANTED,
        mode = TunerMode.CHROMATIC,
        preset = null,
        selectedStringIndex = null,
    )

    @Test
    fun listeningMessageIsShortAndInviting() {
        val s = state(TunerStatus.LISTENING)
        assertTrue(s.statusHint!!.contains("Play", ignoreCase = true), "got '${s.statusHint}'")
        assertTrue(s.statusHint!!.length <= 24, "primary message must be short, got '${s.statusHint}'")
        assertFalse(s.meterActive, "no live reading while listening")
        assertNull(s.noteLabel)
        assertNull(s.frequencyText)
        assertNull(s.centsText)
    }

    @Test
    fun staleMessageSaysSignalLostConcisely() {
        val s = state(TunerStatus.STALE)
        val h = s.statusHint!!
        assertTrue(h.contains("lost", ignoreCase = true) || h.contains("signal", ignoreCase = true), "got '$h'")
        assertTrue(h.length <= 32, "primary message must be short, got '$h'")
        assertFalse(s.meterActive, "a stale reading is not live")
    }

    @Test
    fun weakSignalMessageIsConcise() {
        val s = state(TunerStatus.INSUFFICIENT_SIGNAL)
        val h = s.statusHint!!
        assertTrue(h.contains("weak", ignoreCase = true) || h.contains("closer", ignoreCase = true), "got '$h'")
        assertTrue(h.length <= 32, "primary message must be short, got '$h'")
        assertFalse(s.meterActive)
    }

    @Test
    fun clippingMessageIsActionableAndConcise() {
        val s = state(TunerStatus.CLIPPING)
        val h = s.statusHint!!
        assertTrue(h.contains("clip", ignoreCase = true), "got '$h'")
        assertTrue(h.length <= 40, "primary message must be short, got '$h'")
        assertFalse(s.meterActive, "clipping deviation is not trustworthy")
    }
}