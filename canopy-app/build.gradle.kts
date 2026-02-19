plugins {
    // only if you want ktlint enabled for this module
    alias(libs.plugins.ktlint)
}

// If you need this flag here (recommended: compute here, no coupling to root)
val enableGraalNative: Boolean = providers
    .gradleProperty("enableGraalNative")
    .map(String::toBoolean)
    .orElse(false)
    .get()

dependencies {
    // Canopy deps
    implementation(projects.canopyCore)

    // Gdx
    api(libs.gdx.core)
    api(libs.gdx.backend.headless)
    api(libs.universal.tween)

    // Ktx
    api(libs.ktx.app)
    api(libs.ktx.assets.async)
    api(libs.ktx.assets)
    api(libs.ktx.async)
    api(libs.ktx.log)
    api(libs.ktx.math)

    // Logging
    runtimeOnly(libs.logback.classic)

    // Graal helper only when enabled
    if (enableGraalNative) {
        implementation(libs.graal.helper.annotations)
    }
}
