package dev.anima.resonance.presentation

import dev.anima.resonance.music.BuiltInTunings
import dev.anima.resonance.music.Note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainScreenUiStateTest {

    // ---------- meter mapping: center, limits, clamping, direction ----------

    @Test
    fun meterPositionCenterIsZero() {
        assertEquals(0.0, MainScreenUi.meterPosition(0), 1e-9)
    }

    @Test
    fun meterPositionFullScaleHitsLimits() {
        assertEquals(1.0, MainScreenUi.meterPosition(50), 1e-9)
        assertEquals(-1.0, MainScreenUi.meterPosition(-50), 1e-9)
    }

    @Test
    fun meterPositionIsLinearWithinRange() {
        assertEquals(0.14, MainScreenUi.meterPosition(7), 1e-9)
        assertEquals(-0.14, MainScreenUi.meterPosition(-7), 1e-9)
        assertEquals(0.5, MainScreenUi.meterPosition(25), 1e-9)
    }

    @Test
    fun meterPositionClampsBeyondFullScale() {
        val out = MainScreenUi.meterPosition(120)
        assertEquals(1.0, out, 1e-9, "out-of-meter must clamp, not overflow the dial")
        val neg = MainScreenUi.meterPosition(-81)
        assertEquals(-1.0, neg, 1e-9)
    }

    @Test
    fun meterClampedFlagOnlyBeyondScale() {
        assertFalse(MainScreenUi.isMeterClamped(50))
        assertFalse(MainScreenUi.isMeterClamped(-50))
        assertTrue(MainScreenUi.isMeterClamped(51))
        assertTrue(MainScreenUi.isMeterClamped(-51))
    }

    // ---------- status matrix ----------

    private fun snapshot(
        status: TunerStatus,
        note: Note? = null,
        freq: Double = 0.0,
        cents: Int = 0,
        inTune: Boolean = false,
        direction: PitchDirection = PitchDirection.FLAT,
        clipping: Boolean = false,
    ) = TunerSnapshot(
        reading = TunerReading(status, note, freq, cents, direction, inTune, 1.0),
        rms = 0.3,
        clipping = clipping,
        sampleRate = 48000,
    )

    @Test
    fun permanentDenialShowsSettingsRouteNotRePrompt() {
        val s = MainScreenUi.toUiState(
            snapshot = null,
            permission = MicPermissionState.DENIED_PERMANENT,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
        )
        assertTrue(s.permissionCtaVisible)
        assertEquals("App settings", s.permissionCtaText)
        val notDenied = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.LISTENING),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
        )
        assertFalse(notDenied.permissionCtaVisible)
    }

    @Test
    fun listeningShowsWelcomingHintNeverFakeZero() {
        val s = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.LISTENING),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
        )
        assertTrue(s.statusHint!!.contains("Play", ignoreCase = true), "welcoming hint, got '${s.statusHint}'")
        assertNull(s.frequencyText, "no fake 0 Hz frequency while listening")
        assertNull(s.noteLabel)
    }

    @Test
    fun flatE2RendersTuneUp() {
        val s = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.MEASURED, Note.fromLabel("E2"), 82.4, -3),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
        )
        assertEquals("E", s.noteLabel)
        assertEquals("2", s.octave)
        assertEquals("82.4 Hz", s.frequencyText)
        assertEquals("-3", s.centsText)
        assertEquals("Tune up", s.directionText)
        assertEquals(-0.06, s.meterPosition, 1e-9)
        assertFalse(s.inTune)
    }

    @Test
    fun inTuneRendersCenteredPointerAndInTuneText() {
        val s = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.MEASURED, Note.fromLabel("A4"), 440.0, 0, inTune = true, direction = PitchDirection.INTUNE),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
        )
        assertEquals(0.0, s.meterPosition, 1e-9)
        assertEquals("In tune", s.directionText)
        assertTrue(s.inTune)
        assertEquals("0", s.centsText)
    }

    @Test
    fun sharpRendersTuneDownWithPlusSign() {
        val s = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.MEASURED, Note.fromLabel("A4"), 441.5, 12, direction = PitchDirection.SHARP),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
        )
        assertEquals("+12", s.centsText, "positive cents must carry an explicit sign")
        assertEquals("Tune down", s.directionText)
    }

    @Test
    fun staleShowsHintAndClearsInTune() {
        val s = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.STALE, Note.fromLabel("A4"), 440.0, 0, inTune = false),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
        )
        assertTrue(s.statusHint!!.contains("signal", ignoreCase = true) || s.statusHint!!.contains("lost", ignoreCase = true), "stale hint, got '${s.statusHint}'")
        assertFalse(s.inTune, "a stale reading must never show a frozen success state")
    }

    @Test
    fun clippingShowsActionableHint() {
        val s = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.MEASURED, Note.fromLabel("E2"), 82.4, -3, clipping = true),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
        )
        assertEquals(TunerStatus.CLIPPING, s.status)
        assertTrue(s.statusHint!!.contains("clip", ignoreCase = true), "clipping hint, got '${s.statusHint}'")
    }

    @Test
    fun captureErrorShowsRetryState() {
        // Capture failure is an orchestrator-level condition (CaptureState.ERROR),
        // surfaced to the UI as its own flag — distinct from silence.
        val s = MainScreenUi.toUiState(
            snapshot = null,
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
            captureError = true,
        )
        assertTrue(s.captureError, "capture error must be distinct from silence")
        assertTrue(s.statusHint!!.contains("capture", ignoreCase = true) || s.statusHint!!.contains("microphone", ignoreCase = true), "got '${s.statusHint}'")
    }

    @Test
    fun instrumentModeShowsStringsLowToHighWithSelection() {
        val preset = BuiltInTunings.standard
        val s = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.MEASURED, Note.fromLabel("E2"), 82.4, -3),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.INSTRUMENT,
            preset = preset,
            selectedStringIndex = 0,
        )
        assertEquals(listOf("E2", "A2", "D3", "G3", "B3", "E4"), s.targetLabels, "low-to-high order with octaves")
        assertEquals(0, s.selectedStringIndex)
    }

    @Test
    fun chromaticModeHasNoStringTargets() {
        val s = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.MEASURED, Note.fromLabel("E2"), 82.4, -3),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = BuiltInTunings.standard,
            selectedStringIndex = null,
        )
        assertTrue(s.targetLabels.isEmpty())
        assertNull(s.selectedStringIndex)
    }
}
