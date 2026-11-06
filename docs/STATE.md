# STATE — Resonance

- Settings & custom tunings IN PROGRESS — model/repo/persistence DONE; next: customs persistence + sheet mappers
- Last green checkpoint: ./scripts/test-unit.sh exit 0, "discovered tests: 133"
  (47 music + 27 DSP + 25 state/streaming/capture/concert + 15 UI-state +
  5 headless Compose UI + 10 settings/preset-store + 4 DataStore = 133).
- Next action: (a) UI wiring — settings sheet (concert pitch
  control 400..480, spelling, favorites) + preset picker using PresetStore,
  driven by MainScreenUiState (mapper extension is the TDD target: pure,
  commonTest); (b) import/export JSON codec for custom presets already exists
  (preset JSON codec) — persistence of customs+favorites into DataStore (the DataStore schema has
  the favorites key; add customs JSON key) — TDD in jvmTest against real
  files; (c) Android document-picker boundary = BLOCKED (missing SDK), document.
- UI facts: headless Compose SEMANTICS tests work (skiko native 0.150.2
  pinned in jvmTest). Screenshots still BLOCKED (no capture API in CMP 1.12.2
  desktop test jar — javap-verified).
- DataStore 1.3.0 facts (see TDD_LOG, DataStore-backed repository section, for full list): key factories =
  doublePreferencesKey/stringPreferencesKey in androidx.datastore.preferences.core;
  File factory is jvmAndAndroid; updateData = durable write; repository lives
  in jvmMain (Android twin pending the SDK); never give the store a TestScope
  (runTest hangs); trailing lambdas bind to the LAST parameter.
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
