plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `java-library`
    `maven-publish`
}

dependencies {
    // Canopy deps
    implementation(projects.engine)
    implementation(projects.tooling.utils)
    implementation(projects.adapters.libgdx)

    // File-only managed logging, available by default without project configuration.
    api(projects.adapters.logback)
}
