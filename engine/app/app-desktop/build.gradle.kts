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
    implementation(projects.engine.core)
    implementation(projects.engine.app.appCore)
    implementation(projects.engine.graphics)
    implementation(projects.engine.data.dataCore)
    implementation(projects.engine.utils)

    // Gdx
    implementation(libs.gdx.backend.lwjgl3)

    // Logging
    runtimeOnly(libs.logback.classic)

    // Graal helper only when enabled
    if (enableGraalNative) {
        implementation(libs.graal.helper.annotations)
    }
}
