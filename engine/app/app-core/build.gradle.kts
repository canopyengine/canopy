plugins {
    // only if you want ktlint enabled for this module
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // Canopy deps
    implementation(projects.engine.core)
    implementation(projects.engine.logging)

    // Ktx
    api(libs.ktx.app)
    api(libs.ktx.assets.async)
    api(libs.ktx.assets)
    api(libs.ktx.async)
}
