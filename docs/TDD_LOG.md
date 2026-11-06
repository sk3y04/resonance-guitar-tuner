# TDD log (RED/GREEN/REFACTOR evidence)

Format: slice ID | test(s) | command | initial failure | final exit | report.

## Note frequency math (COMPLETE)
- Tests: NoteMathTest (a4EqualsConcertPitch, standardLowEIsE2AtAbout8241Hz,
  frequencyScalesExactlyWithConcertPitch, octaveIsOneTwelfthRatio,
  rejectsOutOfRangeConcertPitch)
- Tolerance basis: 1e-3 Hz on absolute E2 (well above FP64 error ~1e-13,
  guards against wrong-formula rounding); 1e-12 on ratios; 1e-9 on A4.
- RED (2026-11-06): `./gradlew :shared:jvmTest` → BUILD FAILED, exit 1;
  shared/build/test-results/jvmTest/TEST-dev.anima.resonance.music.NoteMathTest.xml:
  tests=5 failures=5 (e.g. "Expected <440.0> ... actual <0.0>").
- GREEN (2026-11-06): `./scripts/test-unit.sh` → "discovered tests: 5", PASS,
  exit 0 (log artifacts/logs/test-unit-20261106T101420Z.log). Commit: 2nd commit.
- Reformat note: none needed; implementation is the derived MIDI formula
  freq = concert * 2^((midi-69)/12), midi = 12*(octave+1)+semitones.

## Accidental spelling (COMPLETE)
- Tests: NoteSpellingTest (5): enharmonic A#==Bb, 2-arg ctor parsing
  "C#"/"Db"/"A", E#/Fb wrap-around, pitchClass mod-12, invalid names rejected.
- RED: `./gradlew :shared:jvmTest` → exit 1; NoteSpellingTest tests=5 failures=5
  (NotImplementedError from minimal parsing stub; regression NoteMathTest 5/5 green).
- GREEN: `./scripts/test-unit.sh` → "discovered tests: 10", PASS, exit 0.

## Signed cents (COMPLETE)
- Tests: CentsTest (5): zero at target, ±50c, ±3c, ±1200c octave error (unclamped),
  non-positive rejection. Exact-integer assertions (values constructed at exact
  cent offsets; FP error ~1e-10, far from 0.5 rounding boundary).
- RED: exit 1; CentsTest tests=5 failures=4 (zeroAtTarget passes trivially on 0-stub).
- GREEN: test-unit → "discovered tests: 15", PASS, exit 0.

## Built-in presets (COMPLETE)
- Tests: TuningPresetTest (10): Standard/Drop-D/DADGAD/Open-D/Open-G/Half-Step-Down
  frequencies & labels (low-to-high, strict monotonicity), stable distinct ids,
  unique string ids, concert-pitch derivation (no frequency literals).
- RED: exit 1; tests=10 failures=9 (empty-string-list stub; builtInIds test
  passes on stub by design — id-level guard).
- GREEN: test-unit → "discovered tests: 25", PASS, exit 0.
- TEST CORRECTION (justified): first GREEN attempt failed 2 tests because the
  TEST expectations used conventionally rounded values (G3 "196.00", F#3
  "185.00"). Exact equal-temperament values are 195.9977 / 184.9972 Hz — the
  implementation (derived formula) was already correct; the wrong expectations
  were corrected to 4-dp exact values, tolerance 1e-3 retained. This commit
  was amended (local repo, same slice) so no failing state is labeled green.

## Target matching + chromatic band (COMPLETE)
- Tests: TargetMatcherTest (6), ChromaticTargetsTest (4).
- RED: exit 1; matcher 6/6 + chromatic 2/4 failures (stubs threw/returned empty;
  band-iteration tests pass vacuously on empty lists by design).
- GREEN: test-unit → "discovered tests: 35", PASS, exit 0.

## Versioned preset JSON + label parser (COMPLETE)
- Tests: TuningPresetJsonTest (10), NoteLabelTest (2).
- RED: exit 1; json 9/10 + label 2/2 failures (stubs: decode→[], fromLabel→NIError).
- Design note (before GREEN): round-trip test corrected to not assert the
  builtIn flag survives import — payload entries with builtIn=true are
  deterministically SKIPPED (built-ins always come from code).
- BUG FIXED during GREEN: fromLabel used octave offset 2 for naturals ("E2" →
  empty octave part); corrected to offset 1 for naturals / 2 for accidentals.
- GREEN: test-unit → "discovered tests: 47", PASS, exit 0.

## Conditioning + FrameAssembler + YIN (COMPLETE)
- Tests: ConditioningTest (6), FrameAssemblerTest (7), YinTest (11).
- RED: ./gradlew :shared:jvmTest exit 1 — 23/24 new tests failed
  (NotImplementedError stubs; 1 pass = constructor guard by design).
  Total at RED: 71 tests / 23 failures; all 47 music tests still green.
- Test corrections (test-side bugs, asserted on the failing run, kept strict):
  FloatArray assertEquals is reference equality in kotlin.test — 6 assertions
  corrected to contentEquals. Assertions NOT weakened.
- REAL ALGORITHM FIX found by RED→GREEN: a 3000 Hz tone (out of band) was
  reported as 1000 Hz conf=1.0 — a 3x-period subharmonic lag inside the search
  band. Added step-5 spectral-energy guard to Yin.detect: the candidate must
  carry >=25% relative energy AT its own frequency, else PitchEstimate(0,0).
  This is the octave/subharmonic-error shortcut the contract forbids.
- GREEN: ./gradlew :shared:jvmTest exit 0 — 71/71.
- Policy: no lag below absoluteThreshold => no pitch (freq 0, conf 0) —
  strict, deterministic, no best-guess low-quality output. Threshold range
  0.001..0.6 is the sensitivity knob (tested: 0.001 rejects, 0.5 accepts).

## Streaming equivalence + reset-signal contract (COMPLETE)
- Streaming equivalence: FrameAssemblerTest.streamingEquivalenceSampleBySampleVsBulk
  (1..12 pushed one-by-one == bulk push) — GREEN in the DSP run.
- Discontinuity reset: detector is stateless; a discontinuity is signaled as
  PitchEstimate(0.0, 0.0) (silence/noise/out-of-band/threshold-fail). The
  time-aware SMOOTHING that must reset on that signal is state-machine
  behavior and is TDD'd in the state-machine layer (smoothing/staleness), not here.

## Synthetic acceptance matrix + benchmark (COMPLETE)
- Tests: YinMatrixTest (3): full-band sine scan 55..1199 Hz step 2 (573 cases,
  1% relative tolerance, conf>0.5 — octave-error guard), sawtooth fundamentals
  at 9 guitar frequencies (1% tolerance), benchmark.
- RED→GREEN: written as new acceptance criteria for the existing streaming DSP detector;
  run green on first compile fix (test-file type errors only).
- Benchmark (host measurement, NOT an Android latency claim):
  yin.detect median=949us p95=959us, n=300, frame=2048, sr=48000,
  AMD Ryzen Threadripper PRO 5955WX @ ~3.3 GHz, JDK 21, 32 cores.
- EXIT: ./scripts/test-unit.sh exit 0 — "discovered tests: 74" PASS.
  DSP exit criteria: synthetic targets met, edges reject, no caller-buffer
  mutation, no octave-error shortcut.

## Tuner state machine (COMPLETE)
- Tests: TunerStateMachineTest (8): first-pitch hysteresis, consecutive-agreement
  target switch, smoothing reset on discontinuity, stale window + recovery,
  in-tune enter(+5c)/exit(+8c) bands, signed out-of-meter cents, low-confidence
  rejection, manual target semantics (changes cents, not measurement).
- RED: exit 1 — 8/8 NotImplementedError on stub; 82 tests total, prior 74 green.
- Test corrections (own fixtures, asserted on failing runs, strict kept):
  (a) "+6c exactly" fixture hits the Int-truncation boundary (round-trips to 5c)
      -> moved to +7c/+10c, away from band edges; (b) in-tune sequence: machine
      legitimately STARTS in-tune after 0c stabilization, so an explicit +10c
      exit step precedes the enter-band check; (c) 452Hz is +47c not +55c.
- GREEN: exit 0 — 82/82. ./scripts/test-unit.sh "discovered tests: 82" PASS.
- Design: virtual clock (nowMs) injected per frame; median-of-5 smoothing;
  PitchEstimate(0,0) or conf < minConfidence = discontinuity (resets candidate
  + smoothing); manual target bypasses chromatic matching.
