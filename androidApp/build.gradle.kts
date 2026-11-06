plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "dev.anima.resonance.android"
    // Compose 1.12.x artifacts require compileSdk >= 37 (AAR metadata);
    // targetSdk stays at 36 for the Play-API floor.
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.anima.resonance"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    // No org.jetbrains.kotlin.android: AGP 9 has built-in Kotlin support.
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.coroutines.core)
}
