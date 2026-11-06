# STATE — Resonance

- Phase: 1 (musical domain)
- Last green checkpoint: NoteMathTest 5/5 via ./scripts/test-unit.sh (commit after the scaffold)
- Next action: accidentals — write NoteSpellingTest (enharmonic equality,
  parsing of "C#"/"Db", E#==F wrap, invalid spelling rejected, pitchClass),
  add minimal stub (Spelling enum, 2-arg ctor throwing for accidentals,
  pitchClass stub), observe RED, then implement. Then signed cents.
- Versions: Kotlin 2.4.21, AGP 9.3.1, Gradle 9.7.0 (wrapper, sha256-pinned),
  CMP 1.12.2, CMP-Material3 1.9.0, coroutines 1.11.0, serialization 1.12.0,
  lifecycle 2.11.0, datastore 1.3.0, activity-compose 1.13.0, compileSdk 36, minSdk 26, JDK 21 toolchain (JVM_17 bytecode).
- Key paths: repo root; Gradle user-local 9.7.0;
  SDK cmdline-tools at ANDROID_HOME (NO platforms — licenses not accepted).
- Blockers: Android SDK licenses; no display/Xvfb; no emulator/phone.
- Failing test: none yet (red state imminent on NoteMathTest).
