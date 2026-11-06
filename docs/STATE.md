# STATE — Resonance

- Integration COMPLETE (engine/controller/host done; verification running) → project
  at final verification; only external blockers remain (SDK, display, device).
- Last green checkpoint: ./scripts/test-unit.sh exit 0, "discovered tests: 167".
- Test breakdown: 47 music + 27 DSP + 25 state/streaming/capture/concert +
  18 UI-state (15+3 sheet) + 10 headless Compose UI (7 screen + 3 host) +
  21 persistence + 7 AppTuner + 6 TunerController = 167.
- Final verification in flight: ./scripts/verify.sh (background job) — final gates.
- Architecture (final): AppTuner (engine, common) → TunerController
  (permissions/errors/UI mapping, common) → MainScreen (Compose host,
  common) → Android shell (ViewModel + pump thread + Activity, androidMain,
  Compile-blocked on the missing SDK). DataStoreSettingsRepository is commonMain
  (injected DataStore); file factory jvmMain, Context factory androidMain.
- Next after verify PASS: commit the verification slice (docs + verify evidence) and report
  the honest final state (all host-verifiable behavior is tested green).
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
  1.13.0, skiko native 0.150.2 (jvmTest), compileSdk 36, minSdk 26, JDK 21
  (JVM_17 bytecode).
- Key paths: repo root; Gradle
  user-local 9.7.0; SDK at ANDROID_HOME (NO platforms).
- Blockers: Android SDK licenses (compile/APK/device + AndroidAudioInput +
  document picker); screenshots/visual inspection only (semantics tests
  pass headless); no device.
- Failing test: none.
