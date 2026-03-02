plugins {
    // only if you want ktlint enabled for this module
    alias(libs.plugins.ktlint)
}

dependencies {
    // Canopy deps
    implementation(projects.engine.app.appCore)
    implementation(projects.engine.logging)

    // Gdx
    implementation(libs.gdx.backend.headless)

    // Logging
    runtimeOnly(libs.logback.classic)
}
