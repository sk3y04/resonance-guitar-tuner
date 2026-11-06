# STATE — Resonance

- Device/SDK follow-up — SDK UNBLOCKED (2026-11-06): first real Android build GREEN
  (compile + lint + assembleDebug → androidApp-debug.apk). 204/204 host
  tests + demo 5/5 still green. Only the device run remains.
- Last green checkpoint: :shared:jvmTest 204/204 (2026-11-06);
  ./scripts/run-demo.sh 5/5 exit 0; verify.sh ALL mandatory gates PASS
  (Android compile/lint/APK green; device gate not run).
- Test breakdown: 204 = music 47 + DSP 27 + state/stream/capture 25 +
  target-set 4 + UI-state 21 + headless Compose UI 15 + persistence 21 +
  AppTuner 14 + TunerController 10 + editor/codec/concert/others.
  Verify per-run via test-results XML, not this snapshot.
- Runnable demo: ./scripts/run-demo.sh → :hostDemo (5 assertive scenarios;
  [2] = instrument AUTO → lock E2 → unlock; mandatory verify.sh gate 2/4).
- Architecture: AppTuner (engine, common: capture orchestration, YIN,
  state machine, PresetStore targets, custom CRUD + persistCustoms hook,
  AUTO/lock targeting, ±0.1 Hz concert steppers) → TunerController
  (permissions/errors/UI mapping + CRUD facade, common) → MainScreen
  (Compose host, common) → Android shell (ViewModel + pump thread +
  Activity, androidMain, compile-blocked on the missing SDK).
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
