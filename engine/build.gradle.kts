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
    api(projects.tooling.utils)

    // Kotlin
    api(libs.coroutines.core)

    // Gdx
    api(libs.gdx.core)

    // Ktx
    api(libs.ktx.assets)

    // Serialization
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)
    implementation(libs.tomlkt)

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
