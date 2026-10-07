plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
    `maven-publish`
}

dependencies {
    // Canopy
    implementation(projects.tooling.utils)
    implementation(projects.engine)

    // Kotlin
    api(libs.coroutines.core)

    // Mordant
    api(libs.mordant.core)
    api(libs.mordant.coroutines)

    // Logging
    api(libs.slf4j.api)

    // Testing
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)
}
