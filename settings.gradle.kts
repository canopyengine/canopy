pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        mavenLocal()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        maven("https://jitpack.io")
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "canopy"
include(
    ":canopy-core",
    ":canopy-input",
    ":canopy-graphics",
    ":canopy-physics",
    ":canopy-utils",
    ":canopy-data",
    ":canopy-test",
    ":canopy-app"
)

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
