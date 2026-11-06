// Runnable host demo: the FULL pipeline (synthetic mic → AppTuner →
// TunerController → UI-state mapping) as an executable, assertive program.
// No device, no display, no SDK required. Deterministic: virtual clock.
plugins {
    // Version-less: the Kotlin plugin is already on the build classpath
    // (build-logic kotlin-dsl + root alias); pinning a version here collides.
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.anima.resonance.demo.DemoKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.coroutines.core)
}
