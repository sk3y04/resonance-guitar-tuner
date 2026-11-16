package dev.anima.resonance.dsp

import dev.anima.resonance.music.Note
import dev.anima.resonance.presentation.PitchValidity
import dev.anima.resonance.presentation.TunerStatus
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pitch-lab probe (diagnostic, not a contract test).
 *
 * Writes a measured report to shared/build/pitch-lab/report.md: an
 * adversarial scenario sweep (per-string tracking metrics + the frames where
 * the pipeline lost or mis-identified the note) and the detector cost.
 *
 * Used to establish the BEFORE baseline and to keep the AFTER evidence
 * reproducible: `./gradlew :shared:jvmTest --tests "*PitchLabProbe*"`.
 */
class PitchLabProbe {

    private val out = StringBuilder()

    private fun emit(s: String) = out.append(s).append('\n')

    private data class Scenario(
        val label: String,
        val audio: FloatArray,
        val note: Note,
        val trueCents: Double = 0.0,
        /** Last ms at which the modelled SNR is still ≥ 10 dB. */
        val horizonMs: Long,
        val sampleRate: Int = PitchLabSignals.SR,
    )

    /** Analysis-frame RMS of a signal (ground truth for the SNR horizon). */
    private fun frameRms(audio: FloatArray, ms: Long, frame: Int = 4096, sr: Int = PitchLabSignals.SR): Double {
        val start = (ms * sr / 1000).toInt()
        if (start + frame > audio.size) return 0.0
        var sum = 0.0
        for (i in start until start + frame) sum += audio[i].toDouble() * audio[i]
        return sqrt(sum / frame)
    }

    /** Horizon: last ms where the clean tone still clears the modelled floor. */
    private fun horizonOf(clean: FloatArray, floorRms: Double, sr: Int = PitchLabSignals.SR): Long {
        var last = 0L
        var ms = 0L
        val total = clean.size.toLong() * 1000 / sr
        while (ms <= total) {
            val rms = frameRms(clean, ms, sr = sr)
            val snr = if (floorRms <= 0.0) 999.0 else 20.0 * kotlin.math.log10(rms / floorRms + 1e-12)
            if (snr >= 10.0) last = ms
            ms += 10
        }
        return last
    }

    private fun toneFloorRms(peak: Double, snrDb: Double): Double {
        // Mirror of the harness: floor is relative to the tone's RMS at attack.
        val ref = PitchLabSignals.tone(196.0, 0.4, snrDb = 100.0, peak = peak)
        val rms = sqrt(ref.sumOf { it.toDouble() * it } / ref.size)
        return rms / Math.pow(10.0, snrDb / 20.0)
    }

    private fun scenarios(): List<Scenario> {
        val list = ArrayList<Scenario>()

        fun tone(
            label: String,
            f0Label: String,
            f0: Double,
            seconds: Double = 4.5,
            cents: Double = 0.0,
            snrDb: Double = 30.0,
            timbre: PitchLabSignals.Timbre = PitchLabSignals.Timbre(),
            mic: PitchLabSignals.Mic = PitchLabSignals.Mic(),
            inharmonicity: Double = 0.0,
            noisePink: Boolean = false,
            humHz: Double = 0.0,
            humDb: Double = 0.0,
            rumbleHz: Double = 0.0,
            rumbleDb: Double = 0.0,
            decayTau: Double = 2.2,
            sympathetic: Pair<Double, Double>? = null,
            seed: Int = 7,
        ) {
            var audio = PitchLabSignals.tone(
                f0 = f0, seconds = seconds, cents = cents, decayTau = decayTau, snrDb = snrDb,
                timbre = timbre, mic = mic, inharmonicity = inharmonicity, noisePink = noisePink,
                humHz = humHz, humDb = humDb, rumbleHz = rumbleHz, rumbleDb = rumbleDb, seed = seed,
            )
            val clean = PitchLabSignals.tone(
                f0 = f0, seconds = seconds, cents = cents, decayTau = decayTau, snrDb = 100.0,
                timbre = timbre, mic = mic, inharmonicity = inharmonicity, seed = seed,
            )
            if (sympathetic != null) {
                audio = PitchLabSignals.withSympathetic(audio, sympathetic.first, sympathetic.second, rampMs = 700)
            }
            val floor = toneFloorRms(0.5, snrDb)
            list.add(Scenario(label, audio, Note.fromLabel(f0Label), cents, horizonOf(clean, floor)))
        }

        // Clean controls.
        for ((label, f0) in PitchLabSignals.STANDARD) {
            tone("clean $label (30 dB)", label, f0)
        }

        // Quieter room.
        for ((label, f0) in PitchLabSignals.STANDARD) {
            tone("pink 20 dB $label", label, f0, snrDb = 20.0, noisePink = true)
        }

        // Mains hum + rumble (the long-lag corruption case).
        tone("G3 + hum 50Hz -20dB", "G3", 195.997718, snrDb = 25.0, humHz = 50.0, humDb = -20.0)
        tone("E2 + hum 50Hz -20dB", "E2", 82.406889, snrDb = 25.0, humHz = 50.0, humDb = -20.0)
        tone("G3 + rumble 35Hz -18dB", "G3", 195.997718, snrDb = 25.0, rumbleHz = 35.0, rumbleDb = -18.0)
        tone("E2 + rumble 35Hz -18dB", "E2", 82.406889, snrDb = 25.0, rumbleHz = 35.0, rumbleDb = -18.0)

        // Fundamental dies before the 2nd partial (octave-error bait).
        tone(
            "G3 fundamental 2x faster decay", "G3", 195.997718, snrDb = 25.0,
            timbre = PitchLabSignals.Timbre(fundamentalDecayFactor = 2.0),
        )
        tone(
            "E2 fundamental 2x faster decay", "E2", 82.406889, snrDb = 25.0,
            timbre = PitchLabSignals.Timbre(fundamentalDecayFactor = 2.0),
        )
        // Body resonance favouring the octave.
        tone(
            "G3 mic res 392Hz +8dB", "G3", 195.997718, snrDb = 25.0,
            mic = PitchLabSignals.Mic(resonanceHz = 392.0, resonanceGainDb = 8.0, resonanceWidthOctaves = 0.4),
        )
        tone(
            "E2 mic res 165Hz +8dB", "E2", 82.406889, snrDb = 25.0,
            mic = PitchLabSignals.Mic(resonanceHz = 165.0, resonanceGainDb = 8.0, resonanceWidthOctaves = 0.4),
        )

        // Weak fundamental.
        tone("G3 weak fundamental", "G3", 195.997718, snrDb = 25.0, timbre = PitchLabSignals.Timbre(fundamentalGain = 0.3))
        tone("A2 weak fundamental", "A2", 110.0, snrDb = 25.0, timbre = PitchLabSignals.Timbre(fundamentalGain = 0.3))

        // Sympathetic strings (measured G3/D3 relationship, and the 5th).
        tone("G3 + symp D3 -10dB", "G3", 195.997718, snrDb = 25.0, sympathetic = 146.83 to -10.0)
        tone("G3 + symp D3 -14dB", "G3", 195.997718, snrDb = 25.0, sympathetic = 146.83 to -14.0)
        tone("G3 + symp D3 -14dB + pink20", "G3", 195.997718, snrDb = 20.0, noisePink = true, sympathetic = 146.83 to -14.0)
        tone("A2 + symp E2 -12dB", "A2", 110.0, snrDb = 25.0, sympathetic = 82.41 to -12.0)
        tone("E4 + symp B3 -12dB", "E4", 329.627557, snrDb = 25.0, sympathetic = 246.94 to -12.0)

        // Whole-signal stress: quiet attack, deep decay.
        for ((label, f0) in listOf("G3" to 195.997718, "D3" to 146.832384, "E2" to 82.406889)) {
            tone("$label quiet attack 0.05", label, f0, snrDb = 22.0, noisePink = true)
        }

        // Inharmonicity (wound bass strings).
        tone("E2 inharmonic B=4e-4", "E2", 82.406889, snrDb = 28.0, inharmonicity = 4e-4)
        tone("A2 inharmonic B=4e-4", "A2", 110.0, snrDb = 28.0, inharmonicity = 4e-4)
        tone("G3 inharmonic B=4e-4", "G3", 195.997718, snrDb = 28.0, inharmonicity = 4e-4)

        // Detuned targets (a string being tuned).
        for (c in listOf(-30.0, -12.0, -4.0, 4.0, 12.0, 30.0)) {
            tone("G3 ${c.toInt()}c off", "G3", 195.997718, cents = c)
        }
        for (c in listOf(-25.0, 25.0)) {
            tone("E2 ${c.toInt()}c off", "E2", 82.406889, cents = c)
            tone("E4 ${c.toInt()}c off", "E4", 329.627557, cents = c)
        }

        // Karplus-Strong plucks.
        for ((label, f0) in PitchLabSignals.STANDARD) {
            val audio = PitchLabSignals.pluck(f0, 4.0, snrDb = 25.0)
            list.add(Scenario("KS pluck $label (25 dB)", audio, Note.fromLabel(label), 0.0, 2600))
        }
        return list
    }

    @Test
    fun writeReport() {
        emit("# Pitch-lab measured report")
        emit("")
        emit("Deterministic synthetic strings at ${PitchLabSignals.SR} Hz, one analyzed frame per")
        emit("${PitchLab.CHUNK}-sample chunk. Generation: PitchLabProbe.")
        emit("")
        emit("Columns: first = first live reading (ms); correct = first frame on the played")
        emit("note; dropout = analyzed frames inside the usable window that carried no live")
        emit("reading of that note; wrong/octave = frames on a different note; jitter = std-dev")
        emit("of displayed cents; med/p95/maxErr = |displayed cents - true cents|; invalid =")
        emit("ms until the reading went invalid after the signal stopped being recoverable.")
        emit("")
        emit(PitchLab.header())

        val rows = ArrayList<Pair<Scenario, PitchLab.Report>>()
        for (s in scenarios()) {
            val frames = PitchLab.run(s.audio, sampleRate = s.sampleRate)
            val rep = PitchLab.report(
                label = s.label,
                frames = frames,
                expected = s.note,
                trueCents = s.trueCents,
                toMs = s.horizonMs,
            )
            rows.add(s to rep)
            emit(rep.line())
        }

        // Failure detail: detector trace + shown values where tracking broke.
        emit("")
        emit("## Failure detail")
        emit("")
        for ((s, rep) in rows) {
            val bad = rep.dropouts > 2 || rep.wrongNotes > 2 || rep.octaveJumps > 0 ||
                rep.medianAbsErrCents > 3.0 || rep.p95AbsErrCents > 8.0
            if (!bad) continue
            emit("### ${s.label} — dropouts=${rep.dropouts} wrong=${rep.wrongNotes} " +
                "octave=${rep.octaveJumps} medErr=${rep.medianAbsErrCents} p95=${rep.p95AbsErrCents}")
            emit("")
            emit("| ms | rms | det Hz | conf | note | shown Hz | cents | status |")
            emit("|---:|---:|---:|---:|:--:|---:|---:|:--|")
            val frames = PitchLab.run(s.audio, sampleRate = s.sampleRate)
            val yin = Yin()
            var i = 0
            val hop = PitchLab.CHUNK
            val window = 4096
            var detHz = 0.0
            var detConf = 0.0
            var shown = 0
            for (f in frames) {
                // Detector trace on the same window the analyzer used.
                val endSample = (f.ms * s.sampleRate / 1000).toInt()
                val start = endSample - window
                if (start >= 0 && i % 5 == 0) {
                    val win = Conditioning.removeDc(s.audio.copyOfRange(start, start + window))
                    val est = yin.detect(win, s.sampleRate)
                    detHz = est.frequencyHz
                    detConf = est.confidence
                }
                val r = f.reading
                val broken = r.note != s.note || !f.live
                if (broken || shown > 0) {
                    if (i % 2 == 0 || broken) {
                        emit(
                            "| ${f.ms} | ${"%.4f".format(f.rms)} | ${"%.2f".format(detHz)} | " +
                                "${"%.3f".format(detConf)} | ${r.note?.label ?: "—"} | " +
                                "${"%.2f".format(r.frequencyHz)} | ${r.cents} | ${f.status} |",
                        )
                    }
                    if (broken) shown = 24 else shown--
                    if (shown == 0 && broken) shown = 24
                }
                i++
            }
            emit("")
        }

        // ── Dynamic metrics: latency, steps, ramps, re-acquisition ─────────
        emit("## 6. Dynamic behaviour (pipeline end to end)")
        emit("")
        emit("Metric | Value | Condition")
        emit("--- | --- | ---")

        // Invalidation after true silence.
        val silenceTest = PitchLabSignals.concat(
            PitchLabSignals.tone(195.997718, 2.0, snrDb = 25.0),
            PitchLabSignals.silence(1.5),
        )
        val sFrames = PitchLab.run(silenceTest)
        val silenceStart = 2000L
        val lastLive = sFrames.filter { it.ms <= silenceStart + 600 }.lastOrNull { it.live }?.ms
        val firstNone = sFrames.firstOrNull { it.ms > silenceStart && it.reading.validity == PitchValidity.NONE }?.ms
        val firstStale = sFrames.firstOrNull { it.ms > silenceStart && it.status == TunerStatus.STALE }?.ms
        emit("Invalid after silence | ${firstNone?.minus(silenceStart)} ms | tone 2 s @25 dB then digital silence")
        emit("STALE after silence | ${firstStale?.minus(silenceStart)} ms | same stream (state-machine window)")
        emit("Last live frame | ${lastLive?.minus(silenceStart)} ms | relative to silence start")

        // Step response: the string is tuned 15 cents up in one step.
        val stepAudio = PitchLabSignals.concat(
            PitchLabSignals.tone(195.997718, 1.2, snrDb = 30.0),
            PitchLabSignals.tone(195.997718, 1.6, cents = 15.0, snrDb = 30.0),
        )
        val stepFrames = PitchLab.run(stepAudio)
        val stepAt = 1200L
        val reached = stepFrames.firstOrNull { it.ms > stepAt && it.live && kotlin.math.abs(it.reading.centsFine - 15.0) <= 1.0 }?.ms
        emit("Step response (15c) | ${reached?.minus(stepAt)} ms | tone step at t = 1.2 s, ±1c band")

        // Ramp lag: 25 cents over 1.8 s.
        val rampAudio = PitchLabSignals.ramp(3.0, 110.0, 25.0, startMs = 500, ms = 1800)
        var worstRamp = 0.0
        for (f in PitchLab.run(rampAudio)) {
            if (!f.live || f.ms < 900 || f.ms > 2400) continue
            val truth = PitchLabSignals.rampCentsAt(25.0, 500, 1800, f.ms.toDouble())
            worstRamp = maxOf(worstRamp, kotlin.math.abs(f.reading.centsFine - truth))
        }
        emit("Ramp lag (25c / 1.8 s) | ${"%.2f".format(worstRamp)} c | max |shown − truth| after settling")

        // Re-acquisition after a string change: E2, a gap, then A2.
        val changeAudio = PitchLabSignals.concat(
            PitchLabSignals.tone(82.406889, 1.2, snrDb = 30.0),
            PitchLabSignals.silence(0.45),
            PitchLabSignals.tone(110.0, 1.5, snrDb = 30.0),
        )
        val changeFrames = PitchLab.run(changeAudio)
        val newNoteAt = 1200L + 450L
        val relock = changeFrames.firstOrNull {
            it.ms >= newNoteAt && it.live && it.reading.note == Note.fromLabel("A2")
        }?.ms
        emit("Re-acquire new string | ${relock?.minus(newNoteAt)} ms | E2 → 450 ms gap → A2")

        // Acquisition latency per string (median over the six strings).
        val acquisitions = ArrayList<Long>()
        for ((_, f0) in PitchLabSignals.STANDARD) {
            val frames = PitchLab.run(PitchLabSignals.tone(f0, 1.0, snrDb = 30.0))
            frames.firstOrNull { it.live }?.ms?.let { acquisitions.add(it) }
        }
        emit(
            "Acquire (6 strings) | ${acquisitions.sorted()[acquisitions.size / 2]} ms median | " +
                "clean 30 dB pluck, first live reading",
        )
        emit("")

        // ── Detector cost ──────────────────────────────────────────────────
        emit("## Detector cost (JVM, one 4096-sample frame)")
        emit("")
        val yin = Yin()
        val audio = PitchLabSignals.tone(196.0, 3.0, snrDb = 30.0)
        val frame = Conditioning.removeDc(audio.copyOfRange(24000, 24000 + 4096))
        repeat(30) { yin.detect(frame, PitchLabSignals.SR) }
        val t0 = System.nanoTime()
        val iters = 200
        repeat(iters) { yin.detect(frame, PitchLabSignals.SR) }
        val perFrameMs = (System.nanoTime() - t0) / 1e6 / iters
        emit("Yin.detect: ${"%.2f".format(perFrameMs)} ms/frame")
        emit(
            "Hop ${PitchLab.CHUNK} samples = ${PitchLab.CHUNK * 1000 / PitchLabSignals.SR} ms → " +
                "${"%.0f".format(perFrameMs / (PitchLab.CHUNK * 1000.0 / PitchLabSignals.SR) * 100)}% of one core",
        )

        val dir = File("build/pitch-lab").apply { mkdirs() }
        File(dir, "report.md").writeText(out.toString())
        assertTrue(perFrameMs > 0.0)
    }
}