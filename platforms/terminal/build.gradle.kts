plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `java-library`
    `maven-publish`
}

// If you need this flag here (recommended: compute here, no coupling to root)
val enableGraalNative: Boolean = providers
    .gradleProperty("enableGraalNative")
    .map(String::toBoolean)
    .orElse(false)
    .get()

dependencies {
    // Canopy core only
    implementation(projects.engine)
    implementation(projects.tooling.utils)

    // Terminal adapter ONLY (no headless, no libgdx)
    implementation(projects.adapters.mordant)

    // Devtools for testing (AppTestDriver, testHeadlessApp, etc.)
    testImplementation(projects.tooling.devtools)

    // Logging
    runtimeOnly(libs.logback.classic)

    // Test dependencies
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)
}
