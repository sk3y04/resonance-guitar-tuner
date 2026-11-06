# STATE — Resonance

- Phase: 0 (bootstrap) → 1 (musical domain)
- Last green checkpoint: none yet (first RED/GREEN pending)
- Next action: finish wrapper (background job), git init + commit scaffold;
  then run `./gradlew :shared:jvmTest` → expect RED on NoteMathTest (stub returns 0.0)
  → implement Note.frequency → GREEN → commit + log.
- Versions: Kotlin 2.4.21, AGP 9.3.1, Gradle 9.7.0 (wrapper, sha256-pinned),
  CMP 1.12.2, CMP-Material3 1.9.0, coroutines 1.11.0, serialization 1.12.0,
  lifecycle 2.11.0, datastore 1.3.0, activity-compose 1.13.0, compileSdk 36, minSdk 26, JDK 21 toolchain (JVM_17 bytecode).
- Key paths: repo root; Gradle user-local 9.7.0;
  SDK cmdline-tools at ANDROID_HOME (NO platforms — licenses not accepted).
- Blockers: Android SDK licenses; no display/Xvfb; no emulator/phone.
- Failing test: none yet (red state imminent on NoteMathTest).
