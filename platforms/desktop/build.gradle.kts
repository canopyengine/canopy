plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
    `maven-publish`
}

dependencies {
    // Canopy deps
    implementation(projects.engine)
    implementation(projects.tooling.logging)

    // Gdx & Ktx

    // / Graphics
    api(libs.gdx.backend.headless)
    val gdxPlatform = libs.gdx.platform.get().module
    val gdxVer = libs.versions.gdx.get()
    api("$gdxPlatform:$gdxVer:natives-desktop")

    // / Physics
    implementation(libs.gdx.box2d.core)
    implementation(libs.gdx.box2d.platform)

    val gdxBox2DPlatform = libs.gdx.box2d.platform.get().module
    api("$gdxBox2DPlatform:$gdxVer:natives-desktop")

    implementation(libs.ktx.body2d)
    implementation(libs.ktx.graphics)

    // Logging
    runtimeOnly(libs.logback.classic)
}
