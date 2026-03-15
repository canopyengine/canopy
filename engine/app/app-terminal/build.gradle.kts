plugins {
    // only if you want ktlint enabled for this module
    alias(libs.plugins.ktlint)
}

dependencies {
    // Canopy deps
    api(projects.engine.app.appCore)
    // implementation(projects.engine.logging)

    // Gdx
    api(libs.gdx.backend.headless)
    val gdxPlatform = libs.gdx.platform.get().module
    val gdxVer = libs.versions.gdx.get()
    api("$gdxPlatform:$gdxVer:natives-desktop")

    // Logging
    runtimeOnly(libs.logback.classic)
}
