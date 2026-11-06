// Resonance — Android-first offline guitar tuner (Kotlin Multiplatform).
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
includeBuild("build-logic")

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "resonance-tuner"
include(":shared")

// Android targets are enabled only when a usable Android SDK (with at least one
// installed platform) is present on this machine. Host (JVM) development and the
// full shared test suite must never be blocked by the absence of an SDK or
// unaccepted SDK licenses. See docs/DECISIONS.md (ADR-003).
val androidSdkUsable: Boolean = run {
    val home = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        ?: return@run false
    val platforms = File(home, "platforms")
    platforms.isDirectory && !platforms.listFiles().orEmpty().isEmpty()
}
if (androidSdkUsable) {
    include(":androidApp")
}
