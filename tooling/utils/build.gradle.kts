plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `java-library`
    `maven-publish`
}

dependencies {
    // Gdx
    api(libs.gdx.core)
    // Ktx
    api(libs.ktx.math)
}
