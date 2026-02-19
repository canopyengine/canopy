plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

dependencies {
    // Canopy
    implementation(projects.canopyCore)
    implementation(projects.canopyData)
    implementation(projects.canopyUtils)

    // JSON
    implementation(libs.kotlinx.serialization.json)
}
