plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(libs.logback.classic)
    implementation(libs.logback.logstash)

    // Testing
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)
}

// Canopy custom tasks
tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            mapOf(
                "Project-Title" to project.name,
                "Project-Version" to rootProject.version
            )
        )
    }
}
