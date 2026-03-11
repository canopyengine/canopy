plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

dependencies {
    // Canopy
    implementation(projects.engine.core)
    // Gdx
    api(libs.gdx.core)

    // Ktx
    api(libs.ktx.assets)

    // Serialization
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)

    // TOML
    api(libs.tomlkt)
}
