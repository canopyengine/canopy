plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

dependencies {
    // Canopy
    implementation(projects.engine.core)
    implementation(projects.engine.data.dataCore)
    implementation(projects.engine.data.dataSaving)
    implementation(projects.engine.utils)

    // JSON
    implementation(libs.kotlinx.serialization.json)
}
