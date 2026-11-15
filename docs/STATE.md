# STATE — Resonance

- Phase: 13 COMPLETE — spatial composition + overlay refinement (2026-11-15).
  Main screen: the meter+readout are ONE brass-rimmed measurement assembly
  (`MeterAssembly`, height-aware `MeterAssemblyLayout` pure logic + tests) —
  the ivory gauge keeps a natural arc-filling aspect, the readout adjoins it
  directly, and the lower controls absorb surplus height (settings anchored
  low). This removes the empty bands above/below the meter at their source
  (the old width-capped, centre-aligned meter in an oversized weight slot).
  Scale labels enlarged (13dp / FLAT-SHARP 12dp), longer ticks; stronger
  walnut grain on the exposed rails; faceplate width capped (480dp) so the
  gauge stays arc-filling on wide screens. Overlay: three regions (header /
  scrolling CALIBRATION+TUNINGS content / fixed New-Import-Export footer),
  clear "A4 · 440 Hz" reference value with labelled ±1 Hz / ±0.1 Hz stepper
  groups, segmented Sharps ♯ / Flats ♭, refined tuning rows (bigger note
  text, brass active highlight, separate star favorite), brass rim. DSP /
  state / permissions / persistence and the semantic contract unchanged.
  Details: docs/TDD_LOG.md (spatial composition + overlay).
- Prior: milestone COMPLETE — structural faceplate redesign + G3 tracking fix
  (2026-11-15). G3 dropout fix (DSP, the G3 dropout fix): G3 dropped after ~1 s because
  sympathetic D3 makes the ~4:3 mix aperiodic and the YIN dip never
  crossed 0.15; added a guarded sub-threshold recovery + spectral-peak
  refinement on the recovery path only (normal path untouched), with
  `G3SympatheticTrackingTest`. Faceplate redesign (UI): one fixed wooden faceplate
  (enclosure → shared inset faceplate → mounted components), header
  removed, compact tuning/A4 label area + labeled TUNINGS & SETTINGS key,
  configuration moved to a scrolling modal `TuningsOverlay` (platform
  back via a hook, no new dependency); explicit scroll fallback for large
  fonts. DSP/state/permissions/persistence and the semantic contract
  preserved. Details: docs/TDD_LOG.md (G3 dropout fix + faceplate redesign).
- Prior: milestone COMPLETE — instrument refinement pass (2026-11-15). The analog redesign read
  as stacked cards; the refinement makes it one wooden body: horizontal flat-sawn
  walnut grain + tonal bands (deterministic, cached), upper-left lighting,
  thin aged-brass meter frame with larger face / finer needle / cast
  shadow, compact recessed readout strip (note cap 64sp), gold outlines
  removed, proud/flat/pressed/disabled key states, recessed mode track,
  collapsible Tunings & settings sheet (default expanded), tighter
  spacing + bottom inset breathing room. DSP/state/permissions/
  persistence and the semantic contract unchanged. Details:
  docs/TDD_LOG.md (instrument refinement).
- Prior: milestone COMPLETE — analog instrument UI redesign (2026-11-15).
  Walnut enclosure, ivory analog meter, physical needle, brass hardware,
  tactile keys; ui/instrument/ design system; CentsDial removed.
- Prior: milestone COMPLETE — G4 octave-bleed fix (2026-11-15). Playing G4
  (392 Hz) excited the open G string (196 Hz) sympathetically; the detector
  returned 196 Hz or no pitch at −10 dB and the stale guard dropped the G4
  lock. Added a continuous octave-fold in `TunerStateMachine` (a >300 ms
  playing gap still allows a real octave change) and an upper-octave
  recovery in `Yin.detect` when the lower candidate is rejected. Details:
  docs/TDD_LOG.md (G4 octave-bleed fix) + docs/QUALITY_REPORT.md (G4 octave-bleed section). Existing DSP thresholds
  unchanged.
- Prior: milestone COMPLETE — UX/accessibility/reliability/QA audit
  (2026-11-15). 18 verified defects fixed (persistence restore, false
  zero readings, permission/lifecycle, string lock/favorites, flat-note
  editing, theme, responsive layout, a11y names, import I/O bounds,
  ID collisions, destructive delete). Details: docs/TDD_LOG.md (UX/accessibility/QA audit) +
  docs/QUALITY_REPORT.md; host render evidence in shared/build/render/.
  Headless host renders RESOLVED; real-device verification (mic/
  screen reader) is the only remaining external gate.
- Last green checkpoint (2026-11-15, spatial composition): :shared:jvmTest 323/323,
  :shared:testAndroidHostTest 280/280, run-demo 5/5, verify.sh 7/7 PASS
  (offline contract + lint included). New tests: MeterAssemblyLayoutTest 8,
  ReferencePitchValueTest 3. Needle pixel-reverified in the embedded gauge:
  0¢→+1°, −3¢→−2°, +120¢→+46° (stop), no-signal→−46° (rest). Main screen
  pixel-checked: no empty bands around the meter, meter+readout contiguous,
  small phone 360×640 dp unclipped (128 px bottom margin). APK installed
  on the ASUS phone (Android 15) and the new layout confirmed on-device.
  Vision review of the main render positive (rate-limited intermittently);
  overlay verified via semantics tests + PIL. Real-device verification (mic/screen
  reader) remains the only external gate; live-guitar G3 still unverified.
- Prior checkpoint (2026-11-15, faceplate redesign): :shared:jvmTest 312/312,
  :shared:testAndroidHostTest 269/269, run-demo 5/5, verify.sh 7/7 PASS
  (offline contract + lint included). Needle pixel-reverified in the
  faceplate (radial-continuity isolation): 0¢→+1°, −3¢→−2°, +120¢→+44°
  (stop), no-signal→−48° (rest). Small phone 360×640 dp fixed, content
  ends at 93.6% height (no clipping). Device QA NOT run that pass: the
  ASUS phone dropped off wireless adb and was not rediscoverable; the
  vision model was rate-limited (429), so UI evidence was headless Skia
  renders + PIL geometry checks.
- Prior checkpoint (2026-11-15, instrument refinement): :shared:jvmTest 300/300,
  :shared:testAndroidHostTest 258/258, run-demo 5/5, verify.sh 7/7 PASS.
  Needle mapping pixel-reverified after refinement: 0¢→−0.05°,
  −3¢→−2.7°, +120¢→+45.6° (stop), no-signal→−45.7° (rest). Device QA on
  the ASUS phone (1080x2400): grain/readout/bottom-clearance PASS; the
  dimmed band in the no-signal state is intentional (honest rest state).
- Prior checkpoint (2026-11-15, analog redesign): :shared:jvmTest 297/297,
  :shared:testAndroidHostTest 256/256, run-demo 5/5, verify.sh 7/7 PASS;
  debug APK installed on the phone (Android 15) and the API 36 emulator.
- Prior checkpoint (2026-11-15, G4 octave fix): :shared:jvmTest 278/278,
  :shared:testAndroidHostTest 241/241, run-demo 5/5, headless UI PASS,
  debug APK installed on the phone (Android 15) and the API 36 emulator;
  verify.sh gates were last run at the QA audit (see below).
- Prior: device/SDK follow-up COMPLETE + overnight quality program (all five passes)
  (2026-11-08) fixed five DSP/engine/contract bugs (low-E noise,
  subharmonic, stale-note display, two-thread race, offline gate).
  Details + measured quality: docs/QUALITY_REPORT.md.
- DSP correctness facts (2026-11-07): Yin.detect now selects between the
  first-crossing candidate and the global in-band minimum, validated by
  spectral presence (ratio ≥ 0.25) + fundamental plausibility (no in-band
  harmonic 2f..24f > 10x own energy); deeper dip wins, tie → smaller lag.
  StreamingAnalyzer default window = 4096 (93 ms). Pluck fixtures use a
  physical pluck-shape excitation. Contract at 20 dB: 45/45 notes ×
  3 flavors, identity 100%/95%/100%, median ≤5¢/5¢/10¢. 10 dB = documented
  stress limit (identity 41–100% in 87–150 Hz; table in QUALITY_REPORT.md).
- Test breakdown (verify per-run via test-results XML, not this snapshot):
  214 jvm = 204 pre-device-follow-up core + 3 PluckedStringRealism + 4 FrequencyMatrix
  + 3 YinNoiseRobustness. 190 host = same common + 4 Robolectric.
- Runnable demo: ./scripts/run-demo.sh → :hostDemo (5 assertive scenarios;
  [2] = instrument AUTO → lock E2 → unlock; mandatory verify.sh gate 2/4).
- Architecture: AppTuner (engine, common: capture orchestration, YIN,
  state machine, PresetStore targets, custom CRUD + persistCustoms hook,
  AUTO/lock targeting, ±0.1 Hz concert steppers) → TunerController
  (permissions/errors/UI mapping + CRUD facade, common) → MainScreen
  (Compose host, common) → Android shell (ViewModel + pump thread +
  Activity, androidMain; bridge behavior-tested via Robolectric).
- Robolectric host-test facts: AGP-kmp android target gets the
  test source set via `android.withHostTest { }` (no generated accessor —
  deps go in `named("androidHostTest")`); task = :shared:testAndroidHostTest;
  the 176-test common suite ALSO runs there; viewModelScope needs
  `shadowOf(Looper.getMainLooper()).idle()` pumping; Robolectric apps are
  per-test → reset process singletons in @Before (internal test hook).
- DataStore 1.3.0 android facts (Robolectric host suite): filename MUST end in
  preferences_pb (hard extension check); ONE active store per file per
  process → createContextDataStore is a per-process singleton (synchronized).
- Editor UI note: form state lives in MainScreen (remember), not in
  the composable — headless semantics tests drive it via callbacks.
- DataStoreSettingsRepository is commonMain (injected DataStore); file
  factory jvmMain, Context factory androidMain.
- Compose test facts: performScrollToNode = top-level extension in
  androidx.compose.ui.test (import it); assertIsDisplayed is viewport-based —
  assert top-of-container nodes BEFORE scrolling, use performScrollToNode
  for below-fold nodes; Surface(onClick=...) exists in m3 1.9.
- DataStore 1.3.0 facts: key factories doublePreferencesKey/stringPreferencesKey
  (Kotlin names; bytecode @JvmName doubleKey/stringKey); File factory =
  jvmAndAndroid API; updateData = durable write (edit is fire-and-forget);
  repository lives in jvmMain (Android twin pending the SDK); never give the
  store a TestScope (runTest hangs on DataStore's long-lived jobs); trailing
  lambdas bind to the LAST parameter (design signatures accordingly).
- UI facts: headless Compose SEMANTICS tests work (skiko native 0.150.2 in
  jvmTest). Headless PNG rendering works via the public
  `ImageComposeScene.render()` API (2026-11-15); no Xvfb/display needed.
  `TunerScreenRenderSmokeTest` writes dark/light/permission/editor/2x PNGs.
- Versions: Kotlin 2.4.21, AGP 9.3.1, Gradle 9.7.0 (wrapper, sha256-pinned),
  CMP 1.12.2, CMP-Material3 1.9.0, coroutines 1.11.0, serialization 1.12.0,
  lifecycle 2.11.0, datastore + datastore-preferences 1.3.0, activity-compose
  1.13.0, skiko native 0.150.2 (jvmTest), compileSdk 37 (flag suppressed —
  repo has no platforms;android-37, see gradle.properties), minSdk 26, JDK 21
  (JVM_17 bytecode).
- Key paths: repo root; Gradle
  user-local 9.7.0; SDK at ANDROID_HOME (platforms:
  android-36; ANDROID_HOME must be exported for builds).
- Blockers: Android SDK RESOLVED; headless host renders RESOLVED
  (ImageComposeScene); device now attached (ASUS Android 15 over
  wireless adb + API 36 emulator), but the instrumented
  `RealMicSmokeTest` is structurally invalid — it builds
  `CaptureOrchestrator` directly and never calls `poll()`, so it fails on
  both phone and emulator (found 2026-11-15, not fixed here). Real-guitar
  audio/route/screen-reader checks remain manual.
- Failing test: none in the host suites. Device suite: `RealMicSmokeTest`
  fails everywhere for the missing pump (see device-gate note).
