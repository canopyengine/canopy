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

    // Gdx
    implementation(libs.gdx.core)

    implementation(libs.gdx.backend.headless)
    val gdxPlatform = libs.gdx.platform.get().module
    val gdxVer = libs.versions.gdx.get()
    api("$gdxPlatform:$gdxVer:natives-desktop")

    // Ktx
    implementation(libs.ktx.app)
    implementation(libs.ktx.assets)


    // Testing
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
