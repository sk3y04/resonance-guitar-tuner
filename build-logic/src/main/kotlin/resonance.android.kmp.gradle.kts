// Convention plugin: adds the Android target to the KMP `shared` module via the
// official Android-KMP library plugin (com.android.kotlin.multiplatform.library).
// Applied conditionally from shared/build.gradle.kts only when a usable Android
// SDK is present (see settings.gradle.kts). This keeps the JVM host target
// buildable on machines without an SDK while keeping the Android target real
// and enabled wherever the SDK exists.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "dev.anima.resonance.shared"
        compileSdk = (project.findProperty("resonance.compileSdk") as String?)?.toIntOrNull() ?: 36
        minSdk = (project.findProperty("resonance.minSdk") as String?)?.toIntOrNull() ?: 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
