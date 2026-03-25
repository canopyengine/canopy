plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `java-library`
    `maven-publish`
}

// If you need this flag here (recommended: compute here, no coupling to root)
val enableGraalNative: Boolean = providers
    .gradleProperty("enableGraalNative")
    .map(String::toBoolean)
    .orElse(false)
    .get()

dependencies {
    // Canopy deps
    implementation(projects.engine)
    implementation(projects.tooling.utils)
    implementation(projects.platforms.headless)

    // Adapters
    implementation(projects.adapters.libgdx)
    implementation(projects.adapters.mordant)

    // Logging
    runtimeOnly(libs.logback.classic)
}
