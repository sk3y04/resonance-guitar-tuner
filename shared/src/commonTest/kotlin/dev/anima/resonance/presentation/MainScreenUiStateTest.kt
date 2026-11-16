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

    @Test
    fun flatSpellingPreferenceRenamesDisplayedNoteAndTargets() {
        // A#4 reading: under FLAT preference the glyph becomes "Bb", and the
        // Open D preset's F#3 string renders as Gb3. Identity unchanged.
        val flat = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.MEASURED, note = Note.fromLabel("A#4"), freq = 466.16),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.INSTRUMENT,
            preset = BuiltInTunings.openD,
            selectedStringIndex = null,
            accidentalSpelling = dev.anima.resonance.music.Spelling.FLAT,
        )
        assertEquals("Bb", flat.noteLabel)
        assertEquals("4", flat.octave)
        assertTrue(flat.targetLabels.contains("Gb3"), "F#3 must render Gb3: ${flat.targetLabels}")

        val sharp = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.MEASURED, note = Note.fromLabel("A#4"), freq = 466.16),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.INSTRUMENT,
            preset = BuiltInTunings.openD,
            selectedStringIndex = null,
            accidentalSpelling = dev.anima.resonance.music.Spelling.SHARP,
        )
        assertEquals("A#", sharp.noteLabel)
        assertTrue(sharp.targetLabels.contains("F#3"), "default sharp: ${sharp.targetLabels}")
    }

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
        assertEquals("Open app settings", s.permissionCtaText)
        assertTrue(s.permissionCtaOpensSettings)
        assertTrue(s.permissionHint!!.contains("Settings"), "must explain how to recover: ${s.permissionHint}")
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
        assertTrue(s.statusHint!!.contains("Pluck", ignoreCase = true), "welcoming hint, got '${s.statusHint}'")
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
    fun staleShowsHintAndClearsInTuneAndAllNumericReadouts() {
        val s = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.STALE, Note.fromLabel("A4"), 440.0, 0, inTune = false),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
        )
        assertTrue(s.statusHint!!.contains("signal", ignoreCase = true) || s.statusHint!!.contains("lost", ignoreCase = true), "stale hint, got '${s.statusHint}'")
        assertFalse(s.inTune, "a stale reading must never show a frozen success state")
        assertNull(s.noteLabel, "a stale note is history, not a current detection")
        assertNull(s.frequencyText, "stale data must not look like a live measurement")
        assertNull(s.centsText)
        assertNull(s.directionText)
        assertFalse(s.meterActive)
    }

    @Test
    fun weakSignalNeverRendersZeroHzOrACenteredZeroCentsReading() {
        // A real weak frame: the state machine reports no trustworthy pitch
        // even though it may still remember the previous note.
        val s = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.INSUFFICIENT_SIGNAL, Note.fromLabel("E2"), 0.0, 0),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
        )
        assertNull(s.noteLabel, "no current note while the signal is too weak")
        assertNull(s.frequencyText, "never format 0.0 Hz as a measurement")
        assertNull(s.centsText, "no fake 0-cent reading")
        assertNull(s.directionText, "never say 'Tune up' without a measurement")
        assertFalse(s.meterActive, "the pointer must not sit at the center target")
        assertFalse(s.inTune)
        assertTrue(s.statusHint!!.contains("weak", ignoreCase = true) || s.statusHint!!.contains("closer", ignoreCase = true), "got '${s.statusHint}'")
    }

    @Test
    fun clippingKeepsTheNoteButSuppressesTheUntrustworthyDeviation() {
        val s = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.MEASURED, Note.fromLabel("E2"), 82.4, -3, clipping = true),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
        )
        assertEquals(TunerStatus.CLIPPING, s.status)
        assertEquals("E", s.noteLabel, "the note class remains useful context")
        assertNull(s.centsText, "a clipped deviation is not a trustworthy measurement")
        assertNull(s.frequencyText)
        assertNull(s.directionText)
        assertFalse(s.meterActive)
        assertFalse(s.inTune)
    }

    @Test
    fun permissionStatesExplainTheSituationAndOfferTheRightRecovery() {
        fun state(permission: MicPermissionState) = MainScreenUi.toUiState(
            snapshot = null,
            permission = permission,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
        )

        val notRequested = state(MicPermissionState.NOT_REQUESTED)
        assertTrue(notRequested.permissionCtaVisible, "first run needs a visible way to start")
        assertEquals("Allow microphone", notRequested.permissionCtaText)
        assertFalse(notRequested.permissionCtaOpensSettings)
        assertTrue(notRequested.permissionHint!!.contains("microphone", ignoreCase = true))
        assertEquals("Microphone off", notRequested.micStatusText)

        val denied = state(MicPermissionState.DENIED)
        assertTrue(denied.permissionCtaVisible, "a soft denial can be requested again")
        assertEquals("Allow microphone", denied.permissionCtaText)
        assertFalse(denied.permissionCtaOpensSettings)

        val permanent = state(MicPermissionState.DENIED_PERMANENT)
        assertTrue(permanent.permissionCtaVisible)
        assertEquals("Open app settings", permanent.permissionCtaText)
        assertTrue(permanent.permissionCtaOpensSettings)

        val granted = state(MicPermissionState.GRANTED)
        assertFalse(granted.permissionCtaVisible)
        assertNull(granted.permissionHint)
        assertEquals("Listening", granted.micStatusText)
    }

    @Test
    fun grantedButStoppedCaptureIsNotDescribedAsListening() {
        val s = MainScreenUi.toUiState(
            snapshot = null,
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
            captureActive = false,
        )
        assertEquals("Microphone off", s.micStatusText)
        assertTrue(s.statusHint!!.contains("off", ignoreCase = true), "got '${s.statusHint}'")
        assertNull(s.frequencyText)
    }

    @Test
    fun listeningWhileCapturingIsExplicit() {
        val s = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.LISTENING),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.CHROMATIC,
            preset = null,
            selectedStringIndex = null,
            captureActive = true,
        )
        assertEquals("Listening", s.micStatusText)
        assertTrue(s.statusHint!!.contains("Pluck", ignoreCase = true))
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
    fun instrumentAutoExposesTheDetectedStringWithoutClaimingALock() {
        val auto = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.MEASURED, Note.fromLabel("E2"), 82.4, -3),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.INSTRUMENT,
            preset = BuiltInTunings.standard,
            selectedStringIndex = null,
        )
        assertEquals(0, auto.activeStringIndex, "AUTO must show which string it is comparing")
        assertFalse(auto.stringLocked)
        assertNull(auto.selectedStringIndex)

        val locked = MainScreenUi.toUiState(
            snapshot = snapshot(TunerStatus.MEASURED, Note.fromLabel("E2"), 82.4, -3),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.INSTRUMENT,
            preset = BuiltInTunings.standard,
            selectedStringIndex = 2,
        )
        assertEquals(2, locked.activeStringIndex, "a manual lock overrides the AUTO target")
        assertTrue(locked.stringLocked)
    }

    @Test
    fun aReadingFarFromTheLockedTargetSuggestsCheckingTheString() {
        val s = MainScreenUi.toUiState(
            snapshot = snapshot(
                TunerStatus.MEASURED,
                Note.fromLabel("E2"),
                freq = 196.0,
                cents = 1500,
                direction = PitchDirection.SHARP,
            ),
            permission = MicPermissionState.GRANTED,
            mode = TunerMode.INSTRUMENT,
            preset = BuiltInTunings.standard,
            selectedStringIndex = 0,
        )
        assertEquals(0, s.activeStringIndex)
        assertTrue(s.statusHint!!.contains("target string", ignoreCase = true), "got '${s.statusHint}'")
        // The signed deviation is still preserved for the meter/description.
        assertEquals("+1500", s.centsText)
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
