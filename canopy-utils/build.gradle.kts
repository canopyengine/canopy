plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

dependencies {
    // Canopy
    implementation(projects.canopyCore)
    implementation(projects.canopyApp)

    // JSON
}
