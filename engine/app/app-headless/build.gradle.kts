plugins {
    // only if you want ktlint enabled for this module
    alias(libs.plugins.ktlint)
}

dependencies {
    // Canopy deps
    implementation(projects.engine.core)
    implementation(projects.engine.app.appCore)

    // Gdx
    implementation(libs.gdx.backend.headless)

    // Logging
    runtimeOnly(libs.logback.classic)
}
