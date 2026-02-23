plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

dependencies {
    // Gdx
    api(libs.gdx.core)
    // Ktx
    api(libs.ktx.math)
}
