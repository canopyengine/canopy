plugins {
    alias(libs.plugins.ktlint)
}

dependencies {
    // Canopy
    implementation(projects.engine.core)
    implementation(projects.engine.app.appCore)
    implementation(projects.engine.data.dataCore)
    implementation(projects.engine.utils)
    implementation(projects.engine.app.appTest)

    // Gdx
    api(libs.gdx.backend.lwjgl3)
    val gdxPlatform = libs.gdx.platform.get().module
    val gdxVer = libs.versions.gdx.get()
    api("$gdxPlatform:$gdxVer:natives-desktop")

    // Ktx
    api(libs.ktx.graphics)

    // JSON
}
