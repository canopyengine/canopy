plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

dependencies {
    // Canopy
    implementation(projects.engine.core)
    implementation(projects.engine.app.appCore)

    // Gdx
    implementation(libs.gdx.backend.headless)
    val gdxPlatform = libs.gdx.platform.get().module
    val gdxVer = libs.versions.gdx.get()
    api("$gdxPlatform:$gdxVer:natives-desktop")

    // JSON
}
