plugins {
    // only if you want ktlint enabled for this module
    alias(libs.plugins.ktlint)
}

dependencies {
    // Canopy deps
    implementation(projects.engine.core)

    // Ktx
    api(libs.ktx.app)
    api(libs.ktx.assets.async)
    api(libs.ktx.assets)
    api(libs.ktx.async)

    // Logging
    runtimeOnly(libs.logback.classic)
}
