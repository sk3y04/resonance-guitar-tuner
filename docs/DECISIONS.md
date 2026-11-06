# Engineering decisions (dated, with rationale)

## ADR-001 Toolchain baseline (2026-11-06)
- Kotlin 2.4.21 (latest stable per Maven Central metadata).
- AGP 9.3.1, NOT 9.5.1 (latest): KMP 2.4.20–2.4.21 compatibility matrix lists
  AGP 8.5.2–9.3.1 (kotlinlang.org multiplatform-compatibility-guide, read 2026-11-06).
  9.5.1 exceeds the tested ceiling → chose matrix ceiling. Evidence: matrix table.
- Gradle 9.7.0, NOT 9.8.2 (latest): same matrix ceiling. Wrapper distribution
  sha256-pinned (84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae,
  from services.gradle.org official checksum).
- JDK 21 toolchain (host has Temurin 21); bytecode target JVM_17 on both jvm()
  and android targets for consistency.
- CMP 1.12.2 (latest stable). Compose compiler is a SEPARATE plugin
  `org.jetbrains.kotlin.plugin.compose` at the SAME version as Kotlin (2.4.21) —
  confirmed by CMP's own configuration error on first build and by the Central
  plugin marker. (Initial assumption that 1.12.2 merged it was wrong; corrected
  2026-11-06. Matches the research candidate table: "compiler plugin version =
  Kotlin version".)
- Material3 (CMP) pinned explicitly at 1.9.0 (latest stable
  org.jetbrains.compose:material3 on Central; release cycle independent of CMP).
- serialization runtime 1.12.0 (latest stable; fallback 1.11.0 if it proves
  incompatible with the 2.4.21 plugin at build time — not yet observed).
- activity-compose 1.13.0, lifecycle 2.11.0 (KMP-verified via .module metadata:
  common/jvm/androidJvm variants), datastore 1.3.0 (KMP: common/jvm/androidJvm).
- minSdk 26 (product default), compileSdk/targetSdk 36.

## ADR-002 Capture + pitch detection (2026-11-06)
- Default: direct Android AudioRecord backend behind a shared AudioInput
  interface + independently implemented Kotlin YIN (research candidates
  AudioCapture/Klarinet/KaudioAnalyser NOT installed; TarsosDSP GPL code NOT
  used; concepts from YIN paper only). Reversible: swap backend later if
  measured latency/ownership problems appear (oboe escalation path documented).

## ADR-003 Conditional Android target gate (2026-11-06)
- This host has no Android SDK platform installed and SDK licenses cannot be
  accepted on the user's behalf (project contract). AGP fails configuration
  without an SDK, which would block ALL tasks including JVM tests.
- Decision: `:androidApp` (settings.gradle.kts) and the shared android target
  (convention plugin resonance.android.kmp, applied only when
  `$ANDROID_HOME/platforms` is non-empty) are conditionally enabled.
  On any machine with an SDK (e.g. the user's dev box) the full Android build
  is produced from the same sources; on this host all KMP/JVM work + tests run.
- Not a stub: androidMain sources compile into the AAR whenever the SDK exists.
- Unblock (user action, exact command): install the Android SDK platform and accept licenses (user-local sdkmanager).

## ADR-004 Git identity (2026-11-06)
- No global Git identity on host. Per contract, global identity is not changed.
- Repo-local identity set for this project only (user.name "anima",
  user.email "anima@local") so green-slice commits are possible.
