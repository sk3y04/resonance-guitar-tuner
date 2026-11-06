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

## Streaming analyzer (COMPLETE)
- Tests: StreamingAnalyzerTest (6): null-until-first-frame then MEASURED 440Hz
  stream, clipping surface overrides status (CLIPPING), silence stays
  LISTENING with zero frequency (no fake 0Hz success state), caller chunks
  never mutated, stale after silence stream, low-level sine not clipping.
- RED: exit 1 — 6/6 NotImplementedError on stub (after fixing the stub's own
  missing imports); 88 tests total, prior 82 green.
- Test correction (own fixture): 2x1024 samples fill the 2048 ring exactly, so
  the 2nd call yields an UNSTABLE snapshot, not null.
- GREEN: exit 0 — 88/88.
- Pipeline: chunk(copy)->ring->frame->rms+clip(raw)->removeDc->YIN->
  TunerStateMachine->TunerSnapshot. Bounded memory; one snapshot per call
  once ready.

## Capture orchestration (COMPLETE)
- Tests: CaptureOrchestratorTest (8): start idempotent, stop idempotent +
  release, chunk delivery with virtual time, transient error -> RECOVERING ->
  CAPTURING, consecutive errors beyond budget -> ERROR + release + bounded
  (no re-open/no further closes), failed open -> ERROR without release +
  user retry, restart after stop reopens clean, poll-while-idle no-op.
- RED: exit 1 — 8/8 NotImplementedError on stub (96 tests, prior 88 green).
- GREEN: exit 0 — 96/96.
- Design: deterministic pump (poll(nowMs)) + virtual clock; no threads/sleeps;
  recovery budget maxConsecutiveErrors=3; failed open never closed.

## Concert-pitch re-derivation (COMPLETE)
- Tests: TunerConcertPitchTest (3): concert change updates target + re-evaluates
  stale in-tune (440Hz measured @435 ref = +20c SHARP, exits in-tune, re-enters
  at 435Hz), candidate/smoothing reset on change, validation 400..480 inclusive
  (399.9/480.1/0.0 rejected with IAE).
- RED: exit 1 — 3/3 NotImplementedError on stub; 99 tests total, prior 96 green.
- Test correction (own fixture): 1200*log2(440/435)=+19.8c, not +29c as first
  drafted; range fixed to 18..21. Implementation untouched.
- GREEN: exit 0 — 99/99; ./scripts/test-unit.sh PASS (99 discovered).

## Main screen UI state (COMPLETE)
- Tests: MainScreenUiStateTest (15): meter mapping (center 0, limits ±50c,
  clamping ±1 beyond scale, linearity, clamped flag), permanent denial shows
  "App settings" route (no re-prompt), listening hint with no fake 0 Hz,
  flat E2 -> "E"/"2"/"82.4 Hz"/"-3"/"Tune up"/pos -0.06, in-tune center +
  "In tune", sharp "+12"/"Tune down" explicit sign, stale hint + in-tune
  cleared (no frozen success), clipping hint + CLIPPING status, capture error
  distinct from silence (orchestrator-level flag), instrument strings low-to-
  high with octaves + selection, chromatic has no targets.
- RED: exit 1 — 15/15 NotImplementedError; 114 tests total, prior 99 green.
  (captureError added to the stub signature first, per minimal-signature rule.)
- GREEN: exit 0 — 114/114.
- Design: MainScreenUiState is Compose-free; screen renders it, so the whole
  spec state matrix is testable without a display (headless workaround).

## Theme tokens + instrument screen (COMPLETE, compile-verified)
- Code: ui/ResonanceTheme.kt (spec §8 dark-primary tokens #0B1118/#121D29/
  #ECF3F7/#98AABD/#54D6C7/#8AD9A0/#F0B56D/#F18F91 + light secondary, tnum
  tabular numerals), ui/CentsDial.kt (270° partial arc, ±10c ticks, in-tune
  zone, animated pointer, clamped-outside pointer), ui/TunerScreen.kt (note
  glyph+octave, Hz, FLAT/SHARP context, signed cents, direction text, string
  row low-to-high, mode toggle, permission CTA, settings/presets, full test
  tag surface).
- Verification: :shared:compileKotlinJvm EXIT 0 (commonMain Compose compiles
  for the JVM target). No contrived tests for pure layout/theme (spec allows).
- 2026 Compose API notes (verified by compile + javap against ui-desktop
  1.12.2 jar): testTag is now Modifier.testTag (androidx.compose.ui.platform),
  contentDescription is property-assignment in semantics {}, Modifier.weight
  is a RowScope/ColumnScope member (no top-level import), layout package split
  into foundation-layout artifact, @Composable reads must precede Canvas
  lambdas.

## Headless JVM Compose UI tests (COMPLETE)
- Tests: TunerScreenUiTest (5) in jvmTest: listening hint+dial, no fake note;
  flat E2 instrument mode (note/cents/string.0-5 tags); chromatic has no
  string nodes; permission CTA only on permanent denial; clipping keeps dial.
- Infra: ui-test-junit4 1.12.2 (verified 200 on Maven Central) + native skiko
  skiko-awt-runtime-linux-x64 0.150.2 (pinned to CMP 1.12.2's resolved skiko)
  in jvmTest — WITHOUT it: LibraryLoadException "Cannot find
  libskiko-linux-x64.so.sha256".
- RESULT: 5/5 PASS headless (skiko software rendering; no display needed).
  Render-blocker scope NARROWED: semantics/behavior UI tests are unblocked; screenshots
  remain blocked — CMP 1.12.2 desktop test API has NO capture method (verified
  via javap of ui-test-desktop-1.12.2.jar: no capture/screenshot symbols).
- FULL SUITE: ./scripts/test-unit.sh PASS — "discovered tests: 119" exit 0.

## Settings model + repository (COMPLETE)
- Code: persistence/Settings.kt (Settings data class, 400..480 Hz validation,
  DEFAULTS; suspend SettingsRepository contract; InMemorySettingsRepository).
- Tests: SettingsTest (4): defaults, boundary validation (400/480 ok,
  399.9/480.1/0 rejected), repository round trip + failed update leaves state
  intact, favorites toggle by stable ID. GREEN.
- 3 test-fixture self-bugs fixed during GREEN (documented, no weakening):
  asserted 440 against a 400-boundary construct; "No name" was not blank;
  expected G3 at the index that the fixture itself set to G2.

## PresetStore (COMPLETE)
- Code: PresetStore (built-ins immutable, stable "custom-N" IDs, create/edit/
  delete with validation: non-blank name, 1..12 strings, built-in id guard,
  favorites cleared on delete, unknown-id favorite toggle rejected).
- Tests: PresetStoreTest (8): built-ins immutable, stable unique IDs for
  same-name presets, create validation, edit keeps ID + replaces strings,
  delete semantics, favorites survive/clear by ID. GREEN.
- Type note: TuningPreset carries List<TuningString> (id + note), not raw
  Notes — the editor API takes TuningString.

## DataStore-backed repository (COMPLETE, JVM host)
- Code: jvmMain persistence/DataStoreSettingsRepository.kt — REAL
  androidx DataStore 1.3.0 (KMP artifact, jvm target), concert stored as
  milli-hertz double; replace-file corruption handler; read-side decode guard
  rejects out-of-range concert + unknown enum names (fallback to defaults);
  update() uses updateData (suspend, durable) not fire-and-forget edit.
- Tests: DataStoreSettingsRepositoryTest (4, jvmTest, real temp files under
  build/tmp): fresh file -> DEFAULTS; round trip across two instances with
  on-disk byte verification (442000.0 mHz little-endian double); corrupt
  bytes -> defaults AND store remains usable; decode() guard on
  out-of-range stored value. GREEN.
- 2026 DataStore 1.3.0 API facts (verified by jar/klib inspection, the
  androidx/datastore GitHub repo 404s): keys are top-level
  doublePreferencesKey/stringPreferencesKey (bytecode names doubleKey/stringKey
  via @JvmName — the Kotlin names differ); Preferences.Key ctor is internal;
  keys factory class PreferencesKeys is not visible to Kotlin consumers;
  File-based PreferenceDataStoreFactory.create(produceFile = { File }) is the
  jvmAndAndroid API; no public flush — updateData is the durable variant.
- Test infra pitfalls (verified empirically): (1) never give the repository a
  TestScope — DataStore keeps long-lived jobs and runTest hangs forever;
  (2) a trailing lambda binds to the LAST parameter — put the lambda-typed
  param last in public APIs; (3) the round-trip bug was my own helper deleting
  the file between phases (keepFile flag added); (4) ByteArray has no
  containsSequence.
- Catalog: added androidx.datastore:datastore-preferences:1.3.0 (Google
  Maven; jvm+android variants verified in .module metadata).
- FULL SUITE: ./scripts/test-unit.sh PASS — "discovered tests: 133", exit 0.
