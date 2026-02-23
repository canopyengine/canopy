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

    // JSON
}
