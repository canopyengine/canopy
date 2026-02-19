plugins {
    alias(libs.plugins.ktlint)
}

dependencies {
    // Canopy
    implementation(projects.canopyCore)
    implementation(projects.canopyApp)
    implementation(projects.canopyData)
    implementation(projects.canopyUtils)
    implementation(projects.canopyTest)

    // Gdx
    api(libs.gdx.backend.lwjgl3)
    val gdxPlatform = libs.gdx.platform.get().module
    val gdxVer = libs.versions.gdx.get()
    api("$gdxPlatform:$gdxVer:natives-desktop")

    // Ktx
    api(libs.ktx.graphics)

    // JSON
}
