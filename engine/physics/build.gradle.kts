plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

dependencies {
    // Canopy
    implementation(projects.engine.core)
    implementation(projects.engine.app.appCore)
    implementation(projects.engine.app.appTest)

    // Gdx
    implementation(libs.gdx.box2d.core)
    implementation(libs.gdx.box2d.platform)

    val gdxBox2DPlatform = libs.gdx.box2d.platform.get().module
    val gdxVer = libs.versions.gdx.get()
    api("$gdxBox2DPlatform:$gdxVer:natives-desktop")

    // Ktx
    implementation(libs.ktx.body2d)

    // JSON
}
