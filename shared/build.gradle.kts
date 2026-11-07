buildscript {
    // AGP-kmp plugin on THIS module's isolated classpath (not buildSrc —
    // buildSrc exports every dep to the whole build, and a classpath AGP
    // makes versioned `com.android.*` plugin requests fail with
    // "already on the classpath with an unknown version"). The plugin is
    // only APPLIED when a usable SDK is present (ADR-003 gate below), so
    // machines without an SDK never configure it.
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.kotlin.multiplatform.library:com.android.kotlin.multiplatform.library.gradle.plugin:9.3.1")
    }
}
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Enable the Android target only when a usable Android SDK is present.
// (Same gate as settings.gradle.kts — keeps JVM development unblocked.)
val androidSdkUsable: Boolean = run {
    val home = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        ?: return@run false
    val platforms = File(home, "platforms")
    platforms.isDirectory && !platforms.listFiles().orEmpty().isEmpty()
}
if (androidSdkUsable) {
    apply(plugin = "com.android.kotlin.multiplatform.library")

    // AGP-kmp auto-creates the "android" target that unifies the AGP and KMP
    // sides; configure it through the public target interface.
    val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    val android = kotlin.targets.getByName("android")
        as com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
    android.namespace = "dev.anima.resonance.shared"
    // Compose 1.12.x artifacts require compileSdk >= 37 (AAR metadata).
    android.compileSdk = (findProperty("resonance.compileSdk") as String?)?.toIntOrNull() ?: 37
    android.minSdk = (findProperty("resonance.minSdk") as String?)?.toIntOrNull() ?: 26
    android.compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
    // Android host (unit) tests on the JVM via Robolectric — the android
    // side gets real-behavior tests without a device (device-independent).
    android.withHostTest { }
}

kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.datastore)
            implementation(libs.datastore.preferences)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        // Android host tests (Robolectric) — real Context/files on
        // the JVM, so androidMain is behavior-tested without a device.
        // (No generated accessor for withHostTest source sets — use named.)
        named("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.robolectric)
            }
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test.junit4)
            runtimeOnly(libs.skiko.native)
        }
    }
}
