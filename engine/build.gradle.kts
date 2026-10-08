plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.serialization)
    `java`
    `maven-publish`
}

dependencies {
    // Canopy
    api(projects.tooling.utils)

    // Kotlin
    api(libs.coroutines.core)

    // Serialization
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)
    implementation(libs.tomlkt)

    // Logging
    api(libs.slf4j.api)

    // Testing
    // Exercise the core SLF4J bridge against the existing host provider without exporting it at runtime.
    testImplementation(libs.logback.classic)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
