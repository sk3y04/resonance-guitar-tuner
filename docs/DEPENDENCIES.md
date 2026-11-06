# Dependencies — verified 2026-11-06 (UTC)

Pins are in gradle/libs.versions.toml. Verification = live metadata/docs read today.

| Component | Version | Verified source | Notes |
|---|---|---|---|
| Kotlin / KMP | 2.4.21 | repo.maven.apache.org/.../kotlin-stdlib/maven-metadata.xml | latest stable |
| AGP (app + KMP lib) | 9.3.1 | dl.google.com .../com.android.application.gradle.plugin/maven-metadata.xml; KMP compat guide | matrix ceiling for K 2.4.21 (9.5.1 exists, out of matrix) |
| Gradle wrapper | 9.7.0 | services.gradle.org/versions/current (9.8.2 latest; 9.7.0 = matrix ceiling) | sha256 84fbba45… (official .sha256 file) |
| Compose Multiplatform | 1.12.2 | repo.maven.apache.org .../compose-gradle-plugin/maven-metadata.xml | plugin `org.jetbrains.compose`; compose-compiler plugin 2.4.21 applied separately (same version as Kotlin) |
| CMP Material3 | 1.9.0 | repo.maven.apache.org org/jetbrains/compose/material3/material3/maven-metadata.xml | group org.jetbrains.compose.material3; pinned explicitly, independent cycle |
| Coroutines (+test) | 1.11.0 | repo.maven.apache.org kotlinx-coroutines-core metadata | |
| kotlinx-serialization runtime | 1.12.0 | repo.maven.apache.org kotlinx-serialization-core metadata | compiler plugin ships with Kotlin 2.4.21 |
| lifecycle (viewmodel, runtime, runtime-compose) | 2.11.0 | dl.google.com androidx/lifecycle metadata + .module (KMP: common/jvm/androidJvm) | |
| DataStore | 1.3.0 | dl.google.com androidx/datastore metadata + .module (KMP: common/jvm/androidJvm) | experimental API — opt-in scoped to repo impl |
| activity-compose | 1.13.0 | dl.google.com androidx/activity metadata | androidApp only |
| compileSdk / targetSdk | 36 | AGP 9.3.1 supports; Android 16 platform | platform install pending (SDK) |
| minSdk | 26 | product contract | |
| JDK | 21 (Temurin 21.0.12.1) | host | toolchain 21, bytecode JVM_17 |

Not used (research alternatives — deliberately not installed): AudioCapture,
Klarinet, KaudioAnalyser, TarsosDSP (GPL), thetwom/Tuner (GPLv3, concepts only).
No JCenter, no dynamic versions, no resolution overrides.

- androidx.datastore:datastore-preferences:1.3.0 — Google Maven (404 on Central); jvm+android+jvmAndAndroid File factory; key APIs are doublePreferencesKey/stringPreferencesKey top-level in androidx.datastore.preferences.core (1.3.0 rename; @JvmName maps to doubleKey/stringKey in bytecode). DataStoreSettingsRepository lives in jvmMain (Android twin pending the SDK).
