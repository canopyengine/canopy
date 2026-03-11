plugins {
    // only if you want ktlint enabled for this module
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // Canopy deps
    api(projects.engine.core)
    api(projects.engine.logging)
    api(projects.engine.data.dataCore)

    // Ktx
    api(libs.ktx.app)
    api(libs.ktx.assets.async)
    api(libs.ktx.assets)
    api(libs.ktx.async)
}
