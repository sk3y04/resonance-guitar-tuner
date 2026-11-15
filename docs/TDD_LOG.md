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

## Customs persistence + PresetStore hydration (COMPLETE)
- Code: PresetStore(initialCustoms, initialFavorites) hydration + customs()
  accessor; idCounter continues from the max hydrated "custom-N" so new IDs
  never collide. DataStoreSettingsRepository: KEY_CUSTOMS string payload =
  TuningPresetJson.encode(customs); loadCustomPresets/saveCustomPresets;
  single private persist(settings, customs) so a settings update can never
  clobber customs (and vice versa). decodeCustoms recovery: blank/garbage/
  oversized/versioned payloads -> emptyList, never crash.
- Tests: PresetStoreHydrationTest (3, commonTest) + DataStoreCustomPresetsTest
  (4, jvmTest, real files): fresh store empty; round trip deep-equal across
  instances (incl. string ids/notes); save-replaces-previous AND survives a
  subsequent settings update; malformed payload recovery.
- RED: 7/7 failing (NIE stubs, 0 compile errors) -> GREEN 21/21 persistence.

## Preset sheet mappers (COMPLETE)
- Code: presentation/PresetSheetUi (pure): PresetRowUi(id/name/builtIn/
  favorite/selected/stringsPreview low-to-high), toRows (flags by stable id,
  unknown selection selects nothing), concertPitchLabel, concertPitchError
  (400..480), centsLabel (+12 / -3 / 0, zero-band 0.5).
- Tests: PresetSheetUiTest (6) RED 6/6 (NIE stubs) -> GREEN. One fixture
  self-bug fixed: assumed 5 built-ins, there are 7 -> assert from data.
- NEXT: wire the sheet into TunerScreen — extend MainScreenUiState
  with presetRows/concertLabel/concertError + mapper params, render the
  sheet in Compose (compile-verified), then Android document-picker boundary
  = missing Android SDK (document, do not retry).

## Sheet wiring into TunerScreen (COMPLETE)
- Mapper (TDD): MainScreenUiState += presetRows/concertLabel/concertError;
  toUiState += presets/favoritePresetIds/selectedPresetId (additive, default
  values — all 15 UI-state tests unchanged). RED: 21 compile errors (missing
  params/fields) -> GREEN 3/3 (MainScreenSheetStateTest: rows+flags,
  out-of-range concert error, empty defaults).
- Renderer (TDD, headless semantics): TunerScreen += onPresetSelect (default
  {}), sheet section when presetRows non-empty: container tag
  resonance.sheet (bounded heightIn(300dp) + verticalScroll — content
  overflows the screen, sheet must scroll), resonance.sheet.concert
  (label + error text in ResonanceColors.error), resonance.sheet.row.<id>
  (clickable m3 Surface(onClick), selected highlight, favorite star,
  stringsPreview).
- UI tests: +2 (sheet visible w/ rows + concert error; hidden without rows).
  RED: sheet test failed (no tags). Two honest test-ordering bugs found via
  failures: (1) row 8 below the 300dp fold -> assertIsDisplayed is correct
  behavior, fixed by performScrollToNode(hasTestTag(...)) — the row MUST be
  scroll-reachable; (2) concert row asserted AFTER scrolling past it —
  reordered assertions. GREEN 7/7 TunerScreenUiTest.
- performScrollToNode is a top-level extension in androidx.compose.ui.test
  (import required; ActionsKt facade).

## AppTuner integration engine (COMPLETE)
- One engine owns capture + analysis + state + presets + settings;
  caller-driven time/pump (no threads) → fully deterministic.
- Test (7): default start→MEASURED E2 in tune; stop clears + halts flow;
  instrument mode LOCKS the selected string (G3 tone vs E2 target →
  cents > 800, note stays E2); chromatic measures freely; setPreset persists
  + retargets (Drop D / D2 in tune); setConcertPitch(432) persists + 432 Hz
  A4 reads in tune (±1 cent); 3 device losses → RECOVERING → ERROR, stop()
  safe from ERROR.
- RED: throwing stub, 7/7 failing (one fixture self-bug fixed in the test:
  a nonsense sanity line I wrote; and TunerSnapshot exposes .reading —
  test corrected to snap.reading.*, not a behavior change).
- GREEN 7/7 on first implementation run. Suite 158/158. Commit: AppTuner integration engine.

## TunerController (COMPLETE)
- Common bridge: owns AppTuner (capture-state callback can't be forgotten),
  permission gating (DENIED never opens the mic), error surfacing, and
  MainScreenUi.toUiState mapping (instrument mode passes the preset;
  chromatic passes null preset/selectedStringIndex).
- Test (6): permission gates start (0 opens when denied); measured reading
  maps to UI (note/octave/meter/inTune); mode toggle → 6 target labels
  E2..E4; preset+string selection flow; 3 device losses → captureError,
  retry reopens; stop → IDLE + device closed.
- RED: throwing stub 6/6. GREEN: 4/6 first run — my test fixture reset sine
  phase per chunk (discontinuous tone); fixed the fixture (phase is a
  field), not the implementation. GREEN 6/6. Suite 164/164. Commit: TunerController.

## MainScreen host + DataStore KMP cleanup + Android shell (COMPLETE)
- KMP fix: DataStoreSettingsRepository moved jvmMain → commonMain (its only
  dependency is the injected DataStore<Preferences>, which is KMP); File
  factory stays jvmMain, Context factory lands in androidMain. The 8 real-
  file DataStore tests pass UNCHANGED in behavior (construction via the
  factory only).
- MainScreen (Compose, common): ticker re-reads immutable UI state
  (refreshMs param, 50ms default); mutations via controller + immediate
  refresh; TunerScreen gained showSettingsButton/showPresetsButton
  (default true — the 7 headless UI tests unchanged; the host hides them because
  the inline settings sheet replaces them).
- Test (3, headless, REAL controller): E2 tone → rendered note "E"
  (verified via SemanticsProperties.Text, 1.12 API); listening hint before
  start; tap sheet row drop-d → preset label "Drop D". RED: stub 3/3.
  GREEN 3/3 first implementation run. Suite 167/167. Commit: MainScreen host + Android shell.
- Android shell (WRITTEN, compile BLOCKED (missing SDK)): ResonanceViewModel
  (thin AndroidViewModel + PumpWorker thread on the audio clock),
  ContextDataStore (context.createDataStore), ResonanceApp (permission
  policy: granted→start; rationale→request; else DENIED_PERMANENT → CTA to
  app settings, never re-prompts). No device testing.
- Compose 1.12 test API facts: fetchSemanticsNodes is INTERNAL on
  SemanticsNodeInteraction (use the collection's public
  fetchSemanticsNodes(atLeastOneRootRequired=false)); no assertText;
  text read via node.config[SemanticsProperties.Text].

## :hostDemo runnable demo (COMPLETE)
- New Gradle module (kotlin jvm + application) depending on :shared —
  version-less kotlin("jvm") plugin (build-logic kotlin-dsl puts the plugin
  on the classpath; an explicit version collides — "already on the classpath
  with an unknown version").
- One executable program runs the FULL pipeline with a deterministic
  synthetic mic + virtual clock: [1] chromatic E2 in tune; [2] instrument
  mode LOCKS the E2 target under a G3 tone (displayed cents +1500,
  direction "Tune down" — the unclamped cents contract surfaces the octave
  relationship); [3] chromatic measures G3; [4] 432 Hz tone flat (-32) under
  A4=440 → in tune after setConcertPitch(432) (persisted, shown in UI state);
  [5] 3 lost chunks → captureError, retry reopens (deviceOpens=2) → cleared.
- Assertive: any scenario failure exits 1; wired as mandatory gate 2/4 in
  verify.sh + ./scripts/run-demo.sh. Run-verified 5/5, exit 0. (Scenario [2]
  was rewritten for AUTO target selection: instrument mode no longer locks by default — it
  AUTO-selects the played string, then locks/unlocks via tap.)

## Instrument AUTO target selection + manual lock (COMPLETE)
- Spec §2: instrument mode follows the played string (AUTO nearest preset
  string); tapping a string locks it, tapping again unlocks.
- TDD: RED TargetSetTest (4, compile errors: no setTargetSet) →
  TunerStateMachine.targetSetOverride used in onPitch (manual lock still
  overrides the set; setConcertPitch clears the set, engine re-applies);
  AppTuner.lockedString + selectString toggle + applyTarget(set→lock);
  controller passes engine.lockedString (null in AUTO). GREEN 173/173.
- Demo [2] rewritten: G3 tone → AUTO G3 in tune → lock E2 (target switches,
  cents huge) → unlock → G3 again. 5/5. Commit: instrument AUTO target selection.
- Note: A#4 vs E4 is +600 cents (perfect 5th), not +700 — first RED draft
  had the wrong expected value; the implementation was correct, the test
  expectation was fixed (assertion kept strict: 590..610 window).

## Concert-pitch steppers + tenths label (COMPLETE)
- Spec §2 requires a working 0.1 Hz control; it was display-only.
- TDD: RED 10 compile errors (PresetSheetUiTest ×3, TunerControllerTest ×1,
  TunerScreenUiTest ×1 + cascade) → PresetSheetUi.stepConcertPitch (tenth
  rounding + clamp 400..480, long-arithmetic to avoid float drift),
  concertPitchLabel keeps the tenth ("A4 = 439.9 Hz"; whole numbers clean),
  AppTuner/TunerController.stepConcertPersists, TunerScreen −/+ buttons
  (tags resonance.sheet.concert.minus/.plus) wired in MainScreen.
  GREEN 178/178. Commit: concert-pitch steppers.
- Latent bug fixed in-slice: concertPitchLabel used hz.toInt() and would
  have shown "439" for 439.9 — existing integer-only label tests stayed
  green (integer format unchanged).

## Custom-preset CRUD at engine/controller level (COMPLETE)
- Spec §2: custom named tuning creation/editing/deletion. Data layer
  (PresetStore, JSON codec) existed but the engine never exposed
  it.
- TDD: RED 26 compile errors (AppTunerTest ×4, TunerControllerTest ×2) →
  SettingsRepository.saveCustomPresets (interface default pass-through;
  InMemory real storage + customsSnapshot; DataStore override),
  AppTuner persistCustoms hook (last ctor param — trailing lambda),
  createCustomPreset/editCustomPreset (string IDs preserved positionally
  when count unchanged)/deleteCustomPreset (selection fallback to
  "standard" persisted, lock released), TunerController facade + hook.
  GREEN 184/184. Commit: custom-preset CRUD.
- JUnit4 gotcha: `fun test() = runBlocking { assertFailsWith<T> { ... } }`
  infers a non-Unit return type → "Method should be void". Use a block body
  when the last expression is a value-returning assertion helper.

## Custom-tuning editor sheet (COMPLETE)
- Spec §2: creating a custom named tuning. `CustomEditorUi` mapper (note/
  octave cycling, validation, `toNotes`) + `CustomEditorSheet` composable
  (tags `resonance.editor.*`); `TunerScreen` editor param + onAddTuning/
  onEditTuning/onDeleteTuning; `MainScreen` form state in `remember`
  (headless-testable), prefill from stringsPreview, save re-validates.
  Custom rows in the sheet get edit/delete actions; built-ins read-only.
  GREEN 194/194. Commit: custom-tuning editor UI.
- Headless UI gotcha: nodes below the 300dp sheet fold are in the semantics
  tree but `performClick` on off-screen nodes is a no-op — use
  `performScrollToNode` first (see sheet.add in the create-flow test).

## Accidental-preference control ♯/♭ (COMPLETE)
- Spec §2: ♯/♭ display preference for the same pitch. `Note.Spelling` enum
  + `Note.spelledLabel()` (enharmonic conversion, floor division for Cb/Fb).
  Display-only by contract: Note identity, frequencies, stored tunings and
  the codec never change. Fixed a latent display bug in
  `MainScreenUi.toUiState` (accidental glyph was missing from the big label).
  Persists via settings; re-derives targets + sheet rows.
  Tests: NoteSpellingTest (10), MainScreenUiStateTest, TunerControllerTest,
  TunerScreenUiTest. GREEN 199/199 + demo 5/5.

## Import/export feedback (engine + sheet) (COMPLETE)
- Spec §2: import/export + result feedback. `AppTuner.importCustomPresets`
  (versioned JSON, untrusted: codec limits, builtIns never imported, ID
  dedupe `-import`, no mutation on failure) + `exportCustomPresets`;
  `ImportResult`; `PresetStore.addImported` (defense: no ID overwrite);
  sheet Import/Export buttons (`resonance.sheet.import`/`.export`) + result
  text (`resonance.sheet.importResult`); `MainScreen.handleImportPayload`
  (ticker merges the message into mapped state so it survives refresh);
  `onImportPicker`/`onExportPicker` params (Android document picker — SDK-blocked).
  GREEN 203/203 + demo 5/5.
- Gotcha: `ImportResult` landed top-level in the engine package (not nested
  in AppTuner); result text renders below the 300dp fold →
  `performScrollToNode` in the headless test.

## Document-picker wiring (COMPLETE for local gates)
- Spec §2: import/export needs a file source on Android = SAF document
  picker (shell) + a payload handoff the common tests can drive.
- TDD: RED (MainScreen had no `importPayload`/`onImportConsumed`;
  `importPayloadTriggersSheetFeedback`) → GREEN: `MainScreen` params
  `importPayload: String?` + `onImportConsumed`, `LaunchedEffect` handles
  once via the tested `handleImportPayload` (import runs on the composable
  scope so it survives the shell clearing the payload).
  Test: payload → sheet shows "Imported 1" + repo has "P7 Import".
  GREEN 204/204 + demo 5/5 + verify.sh local gates PASS.
- Shell (WRITTEN, compile-blocked on the missing SDK): `ResonanceApp` SAF launchers
  (`OpenDocument` [application/json, text/plain] / `CreateDocument
  "application/json"` → "resonance-tunings.json"); `ResonanceViewModel`
  `onImportUri` (contentResolver read → `importPayload` StateFlow),
  `consumeImport()`, `onExportUri` (contentResolver write of
  `controller.exportCustomPresets()`). Read-only by contract — no storage
  permission requested.
- Kotlin gotcha: a local `fun` in a composable is NOT visible before its
  declaration — place `LaunchedEffect`s that call it after the declaration.
- Next: remaining follow-up work is external-blocker-gated (SDK
  licenses, device). Nothing further is locally actionable.

## Android SDK unblock + first real Android build (COMPLETE)
- User accepted all SDK licenses → installed platform-tools/android-36/
  build-tools (user-local). `./scripts/verify.sh` was the acceptance gate:
  :androidApp compile + lint + assembleDebug.
- Fix 1 (build wiring): build-logic's precompiled `resonance.android.kmp`
  convention plugin was NEVER resolvable — top-level includeBuild doesn't
  feed plugin-ID resolution, and every buildSrc variant hit KGP
  "already on the classpath with an unknown version" (buildSrc leaks deps
  to the whole build; any classpath KGP/AGP breaks versioned plugin
  requests, incl. AGP's transitive KGP 2.2.10). Final design: isolated
  `buildscript { classpath(AGP-kmp marker) }` in shared/build.gradle.kts +
  conditional `apply` (ADR-003 gate intact — SDK-less machines still
  green) + configuring the AGP-kmp AUTO-CREATED `android` target via the
  public `KotlinMultiplatformAndroidLibraryTarget` (namespace/compileSdk/
  minSdk/jvmTarget; `androidTarget()` is forbidden with AGP-kmp).
- Fix 2: AndroidAudioInput.open() used ctor param `rate` in a member
  function (invisible) → `sampleRate`.
- Fix 3: ContextDataStore — `context.createDataStore` doesn't exist in
  DataStore 1.3.0 → `PreferenceDataStoreFactory.create` (jvmAndAndroid
  API) rooted at context.filesDir, IO scope.
- Fix 4: CMP 1.12.2 android variant → Jetpack Compose 1.12.1 AARs need
  minCompileSdk 37; repo snapshot has no platforms;android-37 → official
  `android.experimental.disableCompileSdkChecks=true` (gradle.properties,
  removal condition documented).
- Result: VERIFY_EXIT=0 — all mandatory gates PASS; androidApp-debug.apk
  (~12.8 MB) built; 204/204 host tests + demo 5/5 unchanged.
- Only remaining gate: device smoke.

## Robolectric android-host suite (COMPLETE)
- Goal: behavior-test androidMain WITHOUT a device (device gate still external).
  Robolectric 4.17 (latest stable, live-verified; 4.18 is beta).
- Wiring: AGP-kmp android target gets the host-test source set via
  `android.withHostTest { }` (the earlier build warning documented it);
  NO generated source-set accessor exists for it — deps must go through
  `named("androidHostTest") { dependencies { ... } }`. Test task:
  :shared:testAndroidHostTest. The 176-test common suite ALSO executes
  on the android host (180 = 176 + 4 new).
- RED → GREEN (4 tests):
  1. ContextDataStoreHostTest.contextBackedStoreRoundTripsThroughRealFile
     — real Robolectric Application, real file, fresh-repo re-read.
  2. ResonanceViewModelHostTest.importUriStagesPayloadAndConsumeClearsIt
     — SAF seam: file URI → ContentResolver → staged payload → codec
        import (1/1) → consume clears. Payload made by the REAL codec.
  3. ...exportUriWritesCodecEncodedCustomPresets — file URI out, decode
     round-trip of the written JSON.
  4. ...stopAndClearAreSafeWithoutCapture — lifecycle smoke via a test
     subclass (onCleared is protected; ViewModel.clear() is internal).
- Production bugs the suite CAUGHT (both androidMain, JVM suite couldn't):
  a) DataStore 1.3.0 hard-checks the filename extension — must end in
     `preferences_pb` (was `resonance.preferences`) → crashed on first use.
  b) Two createContextDataStore calls = two active stores on one file →
     IllegalStateException (DataStore is per-process-singleton by contract;
     a recreated ViewModel would have crashed the app). Fixed: synchronized
     per-process singleton + internal resetContextDataStoreForTesting()
     (Robolectric apps are per-test; reset in @Before).
- Test-side gotchas: JUnit4 rejects assertEquals(double,double) without
  delta; viewModelScope coroutines need shadowOf(Looper.getMainLooper())
  .idle() pumping (await-loop with deadline); Uri.fromFile works through
  Robolectric's ContentResolver.
- verify.sh: new mandatory gate 5/5 :shared:testAndroidHostTest (same
  SDK gate as lint/assemble — SDK-less machines stay green via BLOCKED).
- Result: 204/204 jvmTest + 180/180 androidHostTest + demo 5/5;
  verify.sh 5/5 PASS. The device gate now covers ONLY real mic + on-screen UI.

## DSP realism: plucked-string sweep (COMPLETE)
- Goal: prove the YIN pipeline survives a REAL guitar tone (decaying harmonic
  stack), not just sines, before the device gate.
- Generator: Karplus-Strong with EXACT fractional delay —
  y[n] = 0.996 * ((1-α) y[n-M] + α y[n-M-1]), M = floor(sr/f0),
  α = period - M; delay-line indices need +L before Kotlin's % (which keeps
  the dividend's sign) or startup indices go negative. First generator
  draft (2-tap slerp of pair-averages) had a period bias ≈ 6 cents at A2 —
  the test caught it (that's what the 5-cent median assertion is for).
- 3 tests (commonTest → run on BOTH hosts):
  1. 8 plucked frequencies (standard tuning + C3 + E5): >=90% of settled
     frames show the right note, median Hz within 1%, median |cents| < 5,
     and an in-tune lock is reached.
  2. 20 dB room-tone noise on G3: judged on the AUDIBLE portion (>=80% G3,
     tight cents, in-tune lock). Diagnostic first: near the end the pluck
     sits BELOW the noise floor and the pipeline correctly reports
     UNSTABLE (confidence 0.00) instead of asserting — correct behavior,
     so the test judges the audible window, not the decayed tail.
  3. Successive plucks E2→E4: the settled tail must show E4 (the analyzer
     follows the player between strings).
- Outcome: 3/3 GREEN. Every string: 100% correct note, 0 cents,
  confidence 1.00 in the settled window (diagnostic evidence). The DSP is
  ready for a real microphone; remaining device-gate scope is capture/UI only.
- Test data gotcha (self-caught via diagnostic): 130.81 Hz = C3,
  146.83 Hz = D3 — the original map had D3/F3 swapped; the DSP was right,
  the fixture was wrong.

## On-device instrumented suite pre-write (COMPLETE)
- Goal: make the device run a plug-in-and-run event. Suite lives in androidApp
  androidTest (the app module — natural home for Activity + real mic):
  1. RealMicSmokeTest — the real-device core: real AudioRecord reaches CAPTURING
     within 5 s, reader thread delivers PCM within 3 s, ambient RMS > 0.
     RECORD_AUDIO is pm-granted by test-device.sh up front (an unattended
     run can't tap a dialog); otherwise the test ASSUMEs + skips with the
     exact grant command in the message.
  2. DevicePersistenceTest — real Context DataStore round trip + the
     per-process singleton, on the actual framework (no shadows).
  3. AppLaunchSmokeTest — createAndroidComposeRule<ResonanceApp>, asserts
     resonance.note/.preset/.mode exist (state-independent tags, so it
     passes with or without the mic permission).
- Versions (live-verified 2026-11-07): androidx.test core/runner 1.7.0,
  ext:junit 1.3.0, espresso-core 3.7.0, junit 4.13.2; Compose test
  manifest + ui-test-junit4 = androidx 1.12.1 (matches what CMP 1.12.2
  resolves).
- Wiring gotchas: CMP's ui-test-junit4 does NOT resolve in the androidTest
  compile classpath (unresolved org.jetbrains.compose.ui) — the app is
  native Jetpack Compose on android, so use androidx.compose.ui:
  ui-test-junit4 + createAndroidComposeRule (the no-arg createComposeRule
  takes NO type args — wrong rule for an activity). DataStoreSettings
  Repository's constructor names DataStore types and shared declares them
  `implementation` → androidTest needs datastore(-preferences) directly.
- Gates: verify.sh gate 6 = :androidApp:assembleDebugAndroidTest (SDK-gated;
  keeps the device suite buildable without a device). test-device.sh now:
  adb pm grant RECORD_AUDIO → :androidApp:connectedDebugAndroidTest.
- Result: androidApp-debug-androidTest.apk (1.1 MB) builds; verify.sh
  6/6 PASS; jvmTest 207/207 + androidHostTest 183/183 unchanged.

## Overnight quality: DSP correctness map (COMPLETE)
- Goal (unattended night): prove — with a full chromatic matrix — that the
  DSP is correct across 55..1200 Hz, and fix what the matrix finds.
- RED: FrequencyMatrixTest (45 semitones A1..D6 × {sine, physical pluck,
  +20 dB noise}) + YinNoiseRobustnessTest (20-seed 55 Hz @ 10 dB).
  First run found the headline bug: 55 Hz + 10 dB reads A#1 (58.3 Hz) at
  confidence 0.85–1.00 on 20/20 seeds. Diagnostic: noise makes a shallow
  dip (lag 741, d'=0.145) cross the 0.15 threshold before the true-period
  dip (lag ~796, d'=0.069, 7x deeper).
- Fix 1 (GREEN): two-candidate selection in Yin.detect — first-crossing
  descent (fundamental-first) + global in-band minimum, each validated by
  spectral presence (energy ratio ≥ 0.25) and fundamental plausibility
  (no in-band harmonic 2f..24f > 10x own energy — kills subharmonic
  artifacts 110-for-440, 67.8-for-339 (5x), 59.9-for-659 (11x) without
  rejecting overtone-rich real strings). Deeper dip wins; tie → smaller
  lag. 20/20 seeds correct.
- Fix 2: StreamingAnalyzer default window 2048 → 4096 samples (93 ms):
  at 2048, 110 Hz + 10 dB read 86.3 Hz on 27/27 frames (noise-trend dip
  7x deeper than true dip). 8192 measured and rejected (moved failures,
  2x CPU). See docs/QUALITY_REPORT.md.
- Fix 3 (fixture realism, not a product change): Karplus-Strong test
  fixtures used white-noise delay-line init (flat spectrum; some seeds put
  the 4th partial 7x over the fundamental — no physical pluck does that).
  → pluck-shape excitation (30% displacement), 1/h falloff.
- Dead end documented (kept as a comment in Yin.kt): symmetric
  least-squares dip fit removes the noise-trend tilt (measured −12¢ at
  87 Hz @ 10 dB) but shifts clean high-end dips (non-quadratic d′
  curvature over multi-lag windows) — net-negative across the matrix;
  3-point parabola stays. Upgrade path (temporal prior from the state
  machine) is a documented follow-up, out of tonight's scope.
- Known limitation (documented, not hidden): 10 dB SNR degrades identity
  in the 87–150 Hz register (measured 11/27..27/27; full table in
  QUALITY_REPORT.md). The 20 dB realistic-room contract passes strict:
  sine 45/45 @ ≤5¢ (worst 2¢), pluck 45/45 @ ≤5¢ (worst 2¢), noisy 20 dB
  45/45 identity @ ≤10¢ (median 1¢, p90 4¢, worst 10¢ @ 55 Hz).
- Result: jvmTest 214/214, androidHostTest 190/190 (7 new common tests +
  suite); verify.sh 6/6. Next: fuzz/property tests, concurrency
  + soak), contract gates, clean rebuild + report.

## Overnight quality: fuzz/property tests
- Goal (unattended night): property-test every untrusted surface —
  imported payloads, state-machine inputs, engine CRUD storms.
- New suites (commonTest, both hosts):
  - FuzzCodecTest: 400 random byte payloads (only IllegalArgumentException
    acceptable), round-trip identity x50, deterministic ID-collision
    renaming, every documented limit at its boundary (64/65 tunings,
    12/13 strings, 40/41 name, 64/65 id, payload size), builtIn:true in
    file is skipped, unknown keys ignored.
  - FuzzStateMachineTest: 20k pseudo-random frames (freq 55..1200 incl.
    gaps, confidence, backwards/huge clocks, NaN/Inf/negative/1e300
    frequencies, concert-pitch churn 400..480, target-set and manual-lock
    churn). Invariants: no throw, note always in active set, in-tune only
    within exit threshold, direction agrees with cents, garbage never
    corrupts the machine (clean A4 locks after a 500-frame garbage
    storm), concert-pitch contract (400..480 accepted, out-of-band IAE).
  - FuzzCrudTest: 600-op random engine storm (concert/mode/preset/string
    lock/CRUD/favorite/import), garbage imports (must report error,
    mutate nothing), built-ins immutable, IDs unique, repo mirror of
    customs, engine still locks E2 after the storm.
- Bug found by the state-machine fuzz (RED: StaleNoteYieldTest, A1
  displayed at 5210¢): the stable note could outlive its pitch
  indefinitely — rapid target/concert changes reset the hysteresis
  candidate before a re-lock completes, while the 5-frame smoothing ring
  filled with the new (far) frequency. Fix in TunerStateMachine (two
  layers, both gated to chromatic mode — instrument AUTO sets and manual
  locks may show large deviations by design):
  1. staleFrameCount: raw measurement >50¢ from the displayed note for
     more than one full re-lock window (hysteresisFrames) → the note
     yields to UNSTABLE (a legitimate 3-frame transition is untouched —
     pre-existing noteSwitchRequiresConsecutiveAgreement still passes).
  2. Hard backstop: if the SMOOTHED display itself exceeds 200¢ (2
     semitones) the note yields immediately — a near-boundary frame can
     reset layer 1's counter while the ring is still far-dominated
     (caught on the second fuzz pass: A3 at 2307¢ for 5 frames).
- Result: presentation 52/52 (incl. 20k-frame fuzz + stale-yield test),
  full jvm + host suites green, verify.sh gates green. Next: concurrency pass
  (concurrency + soak), contract gates, clean rebuild +
  report).

## Concurrency: two-thread engine (2026-11-08)

- RED: `AppTunerConcurrencyTest` (jvmTest, real threads). Pump thread:
  unpaced `controller.pump(t+=21)` loop (the AudioRecord rhythm,
  accelerated). Main thread: 10 s wall of seeded-random storm — concert
  pitch 400–480 churn, ±0.1 steps, mode flips, preset switches, string
  locks, custom-preset create/edit/delete, favorites, garbage imports,
  plus continuous `state()` reads with a concert-range assert (torn-read
  detector). Contract: no exception escapes either thread; afterwards
  IDs unique, built-ins intact+ordered, settings persist, E2 re-locks in
  tune. First run: `pump thread threw:
  IndexOutOfBoundsException: Empty list doesn't contain element at index
  0` — the state machine's smoothing ring read on the pump thread while
  `setConcertPitch` cleared it on the main thread.
- GREEN: single engine lock. `engineLocked(lock) { ... }`
  (expect/actual over `kotlin.synchronized`; jvmMain + androidMain
  actuals) wraps every read/write of shared state in AppTuner:
  start/stop/poll, applied-settings, lastSnapshot, lockedString,
  captureState, and the new engine-owned UI flags (capturingUi /
  captureErrorUi — the controller's old cross-thread copies are gone).
  Rule 1: lock never held across `settingsRepo` suspensions (pump never
  blocks on disk). Rule 2: all state() reads go through locked getters.
  One compile stumble: `var` + custom getter + `private set` needs an
  initializer → the public properties became `val` over private backing
  vars. One test stumble: JUnit4 `assertEquals(double, double)` without
  delta → added 1e-9.
- Result: jvmTest 228/228, android-host 203/203, verify.sh all mandatory
  gates PASS (device gate still open). Storm clean for 10 s; invariants
  hold; E2 re-locks in tune after the storm.
- Note: an intermediate re-run initially reported the OLD crash because
  `compileKotlinJvm` had failed (my first draft's init error) and Gradle
  left stale results — lesson: clear test-results before a verifying run
  and check the XML mtimes, not just the summary.
- Next: contract gates (merged-manifest permission FAIL gate +
  lint sweep), clean rebuild + final report.

## Contract gates: offline-by-contract (2026-11-08)

- What: `scripts/check-offline-contract.sh`, verify.sh gate 3/7.
  Parses the MERGED debug manifest
  (androidApp/build/intermediates/merged_manifests/debug/processDebugManifest/
  AndroidManifest.xml — app + all libraries after merging) and FAILs on
  INTERNET, network-adjacent permissions, any non-allowlisted permission,
  cleartext traffic, or a wired-in network-security-config. Allowlist:
  RECORD_AUDIO + the AGP-injected
  dev.anima.resonance.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION (both
  documented in the script).
- RED (gate non-vacuity, not a code bug): temporarily injected
  `<uses-permission android:name="android.permission.INTERNET" />` into
  androidApp/src/main/AndroidManifest.xml, re-merged, ran the gate →
  FAIL with all three violation classes reported (INTERNET present,
  network-adjacent, outside allowlist). GATE_EXIT=1.
- GREEN: reverted the manifest (byte-identical, git diff clean),
  re-merged, gate → PASS listing exactly the 2 allowed permissions.
- verify.sh: gates renumbered 6→7; new gate runs before lint/assemble.
  Full run: 7/7 mandatory locally-available gates PASS (device RUN
  still the real-device gate).
- Result: the offline contract is now enforced at build time — a future
  dependency adding a permission fails the build on purpose until the
  spec and the allowlist are amended deliberately.
- Next: clean rebuild + final report.

## Clean rebuild + final report (2026-11-08)

- `./gradlew clean` + manual removal of every module build dir (no
  stale classes/results possible), then full `./scripts/verify.sh`:
  all 7 gates PASS from the wiped state. The contract gate even rebuilt
  its own input ("merged manifest not found — building it..."), proving
  the gate works without pre-existing build state.
- Fresh results: jvmTest 228/228, androidHostTest 203/203 (XML mtimes
  verified post-run), demo 5/5, lint clean, debug APK (12.8 MB) +
  instrumented test APK assembled.
- docs/QUALITY_REPORT.md finalized: bug ledger (5 findings — DSP
  low-E@10dB, subharmonic, stale-note display, two-thread race,
  unenforced offline contract — all RED→GREEN), measured quality
  (431 tests, accuracy contract, concurrency contract, clean rebuild),
  watch items (10 dB low-register table, FuzzStateMachine boundary
  flake, device run).
- Overnight quality program (all five passes): COMPLETE. Remaining
  external-only items: device run and optional visual
  review).

## UX, accessibility, reliability, and QA audit (2026-11-15)

Scope: inspect the shipped Android/shared app rather than re-review the DSP.
No detector algorithm or tuning threshold was changed. Commands used:
`./scripts/test-unit.sh`, `:shared:testAndroidHostTest`, `./scripts/run-demo.sh`,
`./scripts/test-ui.sh`, `./scripts/verify.sh`.

### Verified defects found and fixed (ordered by impact)

1. **Custom tunings and favorites were persisted but never restored.**
   `PresetStore` had hydration support, but the production engine was
   constructed with an empty store and `start()` never asked the repository
   for the saved snapshot. After a relaunch every custom tuning/favorite and
   the selected custom preset disappeared. Fixed by `SettingsRepository
   .loadCustomPresets()` + `PresetStore.hydrate()` + `AppTuner.load()`.
   Regression: `TunerControllerTest.relaunchRestoresCustomTuningsFavoritesAndSelection`,
   `ResonanceViewModelHostTest.customTuningFavoriteAndSelectionSurviveAViewModelRecreation`
   (real DataStore + Robolectric).
2. **Silent/weak/stale/clipping frames could render a false measurement**
   (`0.0 Hz`, `0 cents`, `Tune up`, centered meter). The mapper keyed only on
   `note != null`, while the state machine deliberately returns `frequencyHz
   = 0` when it has no trustworthy pitch. Fixed with an explicit
   `INSUFFICIENT_SIGNAL` status, `meterActive`, and hiding all numeric output
   when there is no live measurement; clipping keeps the note class but
   suppresses the deviation. Regressions: `MainScreenUiStateTest`
   (weak/stale/clipping), `TunerScreenUiTest.readingSurfaceNeverShowsAZeroHz…`.
3. **Android foreground/background lifecycle was unsafe.** Capture was never
   stopped in `onStop`, permission was re-checked only at creation, and the
   pump was a `Thread` that could only be started once — returning to the
   foreground after `stop()` would have thrown `IllegalThreadStateException`.
   Fixed with Activity `onStart`/`onStop`, a reusable parked daemon pump
   (`ResonancePumpWorker`), start-job cancellation, and an engine capture
   epoch so a start suspended on settings I/O cannot open the mic after a stop.
   Tests: `ResonancePumpWorkerTest`, `AppTunerLifecycleTest`.
4. **Permanent denial could re-prompt on every launch**, and first run had no
   in-app explanation before the system dialog. Added `PermissionPolicy`
   (persisted requested flag), user-initiated first request, a visible
   permission banner with recovery, soft-denial retry, permanent-denial
   app-settings route, and permission re-check on foreground.
   Test: `PermissionPolicyTest` (5 cases).
5. **Manual string lock was impossible**: the string tiles had no click
   handler. Fixed + exposed AUTO detection vs a manual lock with text and
   semantics. Tests: `TunerScreenUiTest.stringTargetsAreClickable…`,
   `autoDetectedString…`; `MainScreenUiStateTest.instrumentAuto…`.
6. **Favorites were displayed but could not be toggled.** Added real star
   controls (named for screen readers) and persisted toggling.
   Tests: `TunerScreenUiTest.favoriteToggleIsARealNamedControl`,
   `TunerControllerTest.favoriteToggleFlowsThroughControllerAndPersists`.
7. **Editing a flat custom tuning dropped strings.** The editor rebuilt the
   form by regex-parsing the formatted preview (`[A-G]#?`), so `Eb2`/`Ab2`
   vanished. Replaced with structured `controller.preset(id)` +
   `CustomEditorUi.toEditorString`, and taught the editor to cycle within the
   flat accidental family. Tests: `CustomEditorUiTest.flatSpellings…`,
   `MainScreenUiTest.editingAFlatTuning…`.
8. **Editing the selected tuning did not retarget the live machine** and
   could leave a positional lock pointing at a changed/removed string.
   Fixed in `AppTuner.editCustomPreset` (clear lock + reapply only when the
   edited preset is selected). Test: `editingTheSelectedTuning…`.
9. **Missing selected preset after restore** produced Instrument mode with no
   target strings. Fixed at load time (repair to standard and persist) plus a
   controller mapping fallback. Test: `AppTunerTest.missingSelectedPreset…`,
   `TunerControllerTest.loadRestores…`.
10. **Android shell used plain `MaterialTheme`**, not `ResonanceTheme`; the
    custom light/dark tokens were never active. Fixed, with theme-aware
    typography/dial colors and a contrast test for both schemes.
    Tests: `ResonanceThemeContrastTest` (AA text/graphic floors).
11. **Small-screen/large-text layout defects**: the sheet/editor were clipped
    in a non-scrollable column, the editor had a nested scroll, the Import
    button wrapped to `Impor t`, and mode/action labels clipped at 2× font.
    Fixed with one scrollable instrument surface, a 3×2 string grid on
    phones, adaptive stacked mode/actions/concert controls, and full labels.
    Tests: `smallScreenLargeTextKeepsEveryPrimaryControlReachable`; render
    smoke PNGs inspected at 320/360 dp, 2× font, dark/light.
12. **Discoverability**: the tuning/reference sheet sits below the fold.
    Added a top-bar `Tunings` shortcut (`bringIntoView`) and coarse ±1 Hz
    reference steppers (440→432 is now 8 taps, not 80).
    Tests: `tuningsShortcutJumpsToTheBelowFoldSheet`,
    `coarseConcertSteppersInvoke…`, `PresetSheetUiTest.coarse…`.
13. **Destructive delete was single-tap.** Added a confirmation dialog.
    Test: `MainScreenUiTest.deletingACustomTuningRequiresConfirmation`.
14. **Import/export I/O ran on the main thread and import read unbounded.**
    Moved to `Dispatchers.IO` and added a 1 MiB bounded read.
    Test: `ImportIoTest`.
15. **Imported `custom-N` IDs could collide with the next created tuning**
    (`idCounter` never advanced). Fixed by making `newCustomId()` skip existing
    IDs. Test: `PresetStoreTest.anImportedCustomIdCannotBeReused…`.
16. **Instrumented launch smoke asserted a note even in no-signal states**
    (it could never pass without a current note). Replaced with permission-
    independent surface nodes.
17. **Startup restore raced with user mutations.** Moving the restore off
    the main thread (needed so a permanently denied user still sees their
    settings) exposed a real lost-update race: a custom tuning created while
    the async hydrate was in flight could be overwritten by the restore
    snapshot. The DataStore export test caught it (file decoded to `[]`).
    Fixed with a `persistenceMutex` (restore + every persisted mutation,
    lock order: persistence mutex → engine lock) and a lock-protected
    `AppTuner.uiData()` snapshot for the UI. This also removes the
    cross-thread `PresetStore` read during hydration.

### Test-first / RED notes (honest)

- Core pure contracts were written before the mapping/state changes. The
  first integrated UI run after the scroll restructure did fail (sheet/title
  offscreen, callbacks not invoked); those failures exposed the nested-scroll
  layout and were fixed.
- The render smoke test found visual defects (Import wrapping, clipped
  editor rows, 2× label clipping) rather than asserting pixels; those were
  fixed and re-rendered. There is no screenshot-diff baseline, by design.
- Some API renames and mechanical wiring were done in the same edit as their
  tests; no RED claim is made for those.

### Visual evidence

`TunerScreenRenderSmokeTest` writes PNGs to `shared/build/render/` using
CMP's headless `ImageComposeScene` (dark/light, listening, flat/in-tune,
permission denial, full sheet, custom row, editor, 2× text at 320 dp).
They were inspected during this audit. This is a host Skia render, not an
emulator/device screenshot; real-device rendering remains the real-device gate.

## G4 octave-bleed fix (2026-11-15)
- Report: playing G4, the app locked briefly then lost it (and sometimes
  showed G3); other notes held normally.
- Mechanism: G4 is 392 Hz and the open G string is 196 Hz — exactly one
  octave below. Plucking G4 excites the open G sympathetically, so the
  combined waveform is periodic at 196 Hz. A synthetic sweep of a 196 Hz
  component at −30..0 dB relative to 392 measured: correct 392 Hz down to
  −11 dB, **no pitch at −10 dB**, and G3 from −9..0 dB. The state
  machine's 50-cent stale guard then cleared the G4 lock and re-locked G3.
- RED:
  - `TunerStateMachineTest`: continuous 196 after a locked G4 must keep
    G4; a >300 ms playing gap before 196 must still switch to G3; a
    non-octave change must still switch; a manual G4 lock must also hold.
  - `YinOctaveRecoveryTest`: 392 + 196@−10 dB must recover 392; a true
    196 + 392@−10 dB must stay 196; 20 deterministic noise frames must
    not become a confident pitch.
- GREEN:
  - `TunerStateMachine`: a continuous measurement within 2% of half the
    established note is folded up to that note. A playing gap longer than
    300 ms suppresses the fold so a real octave change is followed.
    Suppression clears when the established note is confirmed again or a
    new note locks.
  - `Yin`: when no candidate survives, look one octave up from each
    rejected candidate (local dip d′ < 0.35, fundamental plausibility,
    and strong tonal energy ratio ≥ 10 to exclude noise) and recover it
    instead of returning no pitch. Existing thresholds are unchanged.
- Evidence: `:shared:jvmTest` 278/278, `:shared:testAndroidHostTest`
  241/241, headless UI suite PASS, demo 5/5. A synthetic G4 replay stays
  on G4 for the whole sympathetic decay instead of blanking and showing
  G3. The fixed APK was installed on the phone (wireless adb) for the
  musical check.
- Known limit: the fold relies on temporal continuity. A real G4→G3
  change with no >300 ms playing gap is acoustically identical to the
  bleed and stays G4 until a gap; a normal string change has that gap.

## Analog instrument UI redesign (2026-11-15)
- Goal: premium physical-instrument feel (dark walnut enclosure, ivory
  analog meter, physical needle, brass hardware, engraved labels, tactile
  keys) WITHOUT touching the audio/tuning pipeline, state mapping,
  permissions, persistence, or the semantic/test contract.
- RED→GREEN slices (new pure logic, tests first):
  - `AnalogMeterGeometryTest` (10): needle angle linear over ±45°, clamped
    at the stops, rest = flat stop (never center), tick spacing 9°/10¢,
    in-tune band = exit tolerance ±8¢, agreement with the tested
    `MainScreenUi.meterPosition`/`isMeterClamped` for −120..120¢.
    RED: unresolved reference → GREEN 10/10.
  - `WoodGrainTest` (5): deterministic streaks per seed+size, bounds,
    finite values, degenerate sizes. RED → GREEN 5/5.
  - `InstrumentPaletteContrastTest` (4): WCAG floors for every
    information-carrying pair (ink/ivory, ivory/walnut+recess, brass/walnut,
    lamps/recess, band/ivory). RED → GREEN 4/4; `bandGreen` added as its
    own RED→GREEN micro-cycle (lamp green failed 3:1 on ivory).
- UI rebuild: `TunerScreen` recomposed from new `ui/instrument/`
  components (WoodenEnclosure, AnalogTuningMeter, NoteReadout +
  TuningStatusIndicator, MechanicalModeSelector, InstrumentStringSelector,
  CalibrationControls/SpellingKeys, InstrumentKey/RecessedPanel/JewelLamp);
  editor + delete dialog restyled; `CentsDial` deleted. The pre-existing
  25-screen semantics suite + 7 controller-driven screen tests + render
  smoke served as the regression contract (no new RED claimed for the
  visual layer itself).
- First integrated run: 297 tests, 2 REAL failures, both fixed:
  1. fine concert stepper tags were `.Minus/.Plus` (string-interpolation
     casing bug) → explicit tag constants; caught by
     `sheetConcertSteppersInvokeCallback`.
  2. at 320 dp / 2× font the note readout fell below the fold (meter-first
     hierarchy) → maker's mark hidden at large text, compact meter aspect
     (1.8 vs 1.6), maker's mark guarded by face height; caught by
     `smallScreenLargeTextKeepsEveryPrimaryControlReachable`.
- CMP 1.12 API facts learned: no `DrawScope.drawText(TextLayoutResult)`
  extension — use `androidx.compose.ui.text.drawText(textMeasurer, …)`
  (TextMeasurer caches measurements); `Brush.verticalGradient` takes
  startY/endY (not start/end); `Canvas` requires an explicit onDraw even
  when all drawing lives in a drawWithCache modifier.
- Performance: grain paths + meter static artwork built in drawWithCache
  stabilized by remember(active, inTune, clamped, compact) — the 20 Hz
  ticker does not rebuild them; only the needle reads animated state.
  Needle spring unchanged from the old dial (0.8 damping / 300 stiffness).
- Evidence: :shared:jvmTest 297/297, :shared:testAndroidHostTest 256/256,
  run-demo 5/5. Render smoke regenerated 15 PNGs (added clamped-sharp and
  no-signal cases). Pixel-verified needle angles: −3¢→−2.6°, 0¢→+0.2°,
  +120¢→+45.5° (pinned), no-signal/listening→−45° rest; NO SIGNAL engraving
  present only without a reading; light/dark system renders byte-identical
  (instrument materials are theme-constant by design).
- verify.sh: 7/7 gates PASS (host tests, demo, offline contract, lint,
  debug assembly, Robolectric host tests, instrumented APK compile).
- Visual QA: headless renders inspected (vision + pixel analysis). Main
  screen passed critical review; FLAT/SHARP engravings promoted from
  secondary ink (4.99:1) to full charcoal ink (12.6:1) for clarity.
  Mode-key engagement, AUTO/LOCKED string states and the large-text fold
  fix additionally verified from rendered pixels.
- Known limits: real-device visual/mic/screen-reader review remains the real-device gate;
  no system reduced-motion API exists in common Compose, so the needle
  keeps the previous spring behavior; tick labels are fixed-dp instrument
  markings (functional readouts scale with system font size).

## Instrument refinement pass (2026-11-15)
- Report: the analog redesign read as "stacked brown UI cards with an analog meter", not
  one wooden instrument; calibration cramped at the bottom on the phone.
  Goal: material realism, composition, small-screen layout — not a
  recolor. Functionality, state, DSP and the semantic contract untouched.
- RED→GREEN slices:
  - `WoodGrainTest` rewritten for HORIZONTAL figure + tonal bands
    (startY bounds, wander ≤ amplitude, band subtlety ≤ 0.2 alpha,
    determinism). RED: unresolved `startY`/`bands` → GREEN 7/7.
  - `sheetToggleCollapsesAndReExpandsSecondaryControls`: the settings
    sheet collapses to its header and re-expands; collapsed content
    leaves the semantics tree. RED: tag not found → GREEN.
- Material/composition changes:
  - Enclosure: horizontal flat-sawn grain (34 streaks, alpha 0.07–0.20)
    + 5 broad tonal bands, upper-left→lower-right lighting overlay,
    shaded outer bevel + inner recess lip, smaller dark-brass corner
    screws. Grain cached per size; never regenerated on ticks.
  - Meter: thin (3 dp) aged-brass frame with hairline inset edge, larger
    face (margins 26→18 dp), 11 dp scale labels, longer ticks, restrained
    4 dp green band, finer needle (1.7 dp half-width) with metal sheen,
    refined brass pivot (seat shadow + dome + specular), subtle clipped
    age mottling on the ivory, cast shadow onto the wood. Maker's mark
    removed from the face (no decorative competition). Geometry/mapping
    unchanged and pixel-reverified: 0¢→−0.05°, −3¢→−2.7°, +120¢→+45.6°,
    no-signal→−45.7°.
  - Readout: one compact recessed strip (note+octave | cents, Hz, jewel
    status), note cap 96→64 sp, cents 44→34 sp, height ~107→~100 dp at
    1x; no dead zone in the no-signal state. Tabular numerals kept.
  - Controls: gold outlines removed everywhere; engaged keys stand proud
    (3 dp shadow + bevel), idle keys sit flat in the recess, finger
    presses dip, disabled dims; mode selector is one recessed track with
    two keys; chromatic helper text shortened to one line.
  - Small screens: settings sheet collapsible (default expanded keeps
    the tested contract), tighter spacing, 24 dp bottom breathing room
    above the navigation insets; scroll root + safeDrawing unchanged.
    320 dp/2x: note glyph bottom at 460 dp of 480 dp (was below fold).
- Evidence: ui suite 57/57, grain 7/7, toggle GREEN; full verify.sh
  result recorded in STATE.md. Device screenshot QA on the ASUS phone
  (1080x2400 @440dpi) via adb; headless renders re-inspected.
- Known limits: real-device mic/screen-reader pass still open;
  vision-model aesthetic reviews are advisory — state claims were
  re-verified against pixels (frame thickness, band heights, needle
  angles) before acting on them.
