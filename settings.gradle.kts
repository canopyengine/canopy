pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        maven("https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "canopy"

// Root modules
include(
    ":engine:core",
    ":engine:input",
    ":engine:graphics",
    ":engine:physics",
    ":engine:data",
    ":engine:saving"
)

// Kits - modules that don't provide end-features
include(
    ":engine:testkit",
    ":engine:app:appkit",
    ":engine:utils"
)

// Backends
include(
    ":engine:app:headless",
    ":engine:app:desktop"
)

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
