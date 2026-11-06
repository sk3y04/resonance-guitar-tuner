plugins {
    `kotlin-dsl`
}
dependencies {
    // KGP: the Gradle-bundled version (kotlin("gradle-plugin")) — pinning a
    // different KGP here conflicts with kotlin-dsl's copy ("unknown version").
    implementation(kotlin("gradle-plugin"))
    implementation("com.android.kotlin.multiplatform.library:com.android.kotlin.multiplatform.library.gradle.plugin:9.3.1")
}
