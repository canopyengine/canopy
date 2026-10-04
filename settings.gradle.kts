pluginManagement {
    includeBuild("tooling/compiler")
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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Platforms
include(
    ":platforms:headless",
    ":platforms:terminal",
    // Keep disabled until stale platform/adapters references are repaired.
    // ":platforms:desktop"
)

// Engine
include(":engine")

// Adapters
include(
    ":adapters:libgdx",
    ":adapters:mordant"
)

// Tooling
include(
    ":tooling:devtools",
    ":tooling:utils"
)

// Register dependency participation as well as early plugin resolution; Gradle keeps one included build.
includeBuild("tooling/compiler")
