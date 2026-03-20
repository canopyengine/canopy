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

    // Gdx
    implementation(libs.gdx.core)

    implementation(libs.gdx.backend.headless)
    val gdxPlatform = libs.gdx.platform.get().module
    val gdxVer = libs.versions.gdx.get()
    api("$gdxPlatform:$gdxVer:natives-desktop")

    // Ktx
    implementation(libs.ktx.app)
    implementation(libs.ktx.assets)

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
