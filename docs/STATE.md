# STATE — Resonance

- Device/SDK follow-up COMPLETE + DSP correctness map + fuzz/property
  + concurrency COMPLETE. 228/228 jvmTest + 203/203 android host
  + demo 5/5 + instrumented test APK builds; verify.sh 6/6 gates PASS.
  The correctness pass fixed a real low-E-at-noise DSP bug (+ subharmonic fragility);
  The fuzz pass found + fixed a stale-note display bug (a locked note could
  outlive its pitch indefinitely under rapid setting changes — 5210¢
  displays) via a bounded-staleness guard + hard backstop; the concurrency pass proved
  the two-thread engine race real (pump crashed with
  IndexOutOfBoundsException in the smoothing ring) and fixed it with a
  single engine lock (expect/actual `synchronized`; persistence kept
  outside the critical section). 10 dB low-register stress documented in
  docs/QUALITY_REPORT.md. Device day = attach device,
  ./scripts/test-device.sh. Remaining: contract gates → clean
  rebuild).
- Last green checkpoint: :shared:jvmTest 228/228 (214 + 13 fuzz:
  FuzzCodec 5 + FuzzStateMachine 4 + FuzzCrud 3 + StaleNoteYield 1 +
  1 concurrency AppTunerConcurrencyTest);
  :shared:testAndroidHostTest 203/203 (199 common + 4 Robolectric);
  run-demo 5/5; verify.sh ALL 6 mandatory gates PASS (device RUN still
  not run; its test APK is assembled as gate 6).
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
  jvmTest). Screenshots BLOCKED (no capture API in CMP 1.12.2 desktop test
  jar — javap-verified).
- Versions: Kotlin 2.4.21, AGP 9.3.1, Gradle 9.7.0 (wrapper, sha256-pinned),
  CMP 1.12.2, CMP-Material3 1.9.0, coroutines 1.11.0, serialization 1.12.0,
  lifecycle 2.11.0, datastore + datastore-preferences 1.3.0, activity-compose
  1.13.0, skiko native 0.150.2 (jvmTest), compileSdk 37 (flag suppressed —
  repo has no platforms;android-37, see gradle.properties), minSdk 26, JDK 21
  (JVM_17 bytecode).
- Key paths: repo root; Gradle
  user-local 9.7.0; SDK at ANDROID_HOME (platforms:
  android-36; ANDROID_HOME must be exported for builds).
- Blockers: Android SDK RESOLVED (first Android build green, see TDD_LOG for
  the 4 fixes); screenshots/visual inspection only (semantics tests
  pass headless); no device (only remaining gate).
- Failing test: none.
