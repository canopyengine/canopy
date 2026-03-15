plugins {
    alias(libs.plugins.ktlint)
}

// If you need this flag here (recommended: compute here, no coupling to root)
val enableGraalNative: Boolean = providers
    .gradleProperty("enableGraalNative")
    .map(String::toBoolean)
    .orElse(false)
    .get()

dependencies {
    // Canopy
    api(projects.engine.utils)
    api(projects.engine.logging)

    // Logging
    api(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

// Canopy custom tasks
tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            mapOf(
                "Project-Title" to project.name,
                "Project-Version" to rootProject.version
            )
        )
    }
}
