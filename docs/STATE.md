# STATE — Resonance

- Settings & custom tunings IN PROGRESS — model/repo/persistence DONE; next: sheet wiring
- Last green checkpoint: ./scripts/test-unit.sh exit 0, "discovered tests: 146".
- Test breakdown: 47 music + 27 DSP + 25 state/streaming/capture/concert +
  15 UI-state + 5 headless Compose UI + 21 persistence (settings, store,
  hydration, DataStore, customs) + 6 preset-sheet mappers = 146.
- Next action: wire the settings/preset sheet into TunerScreen —
  (a) extend MainScreenUiState: presetRows: List<PresetRowUi>,
  concertLabel, concertError, favorites; extend MainScreenUi.toUiState with
  presets/favorites/selectedPresetId/concertPitchHz params (update the 15
  UI-state tests' calls accordingly — additive params with defaults);
  (b) Compose sheet: preset rows (favorite star, built-in chip, selection),
  concert pitch row using PresetSheetUi labels; compile-verified (no new
  fake tests); (c) then audio wiring + integration — Android
  pieces stay documented-blocked (SDK/device).
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
