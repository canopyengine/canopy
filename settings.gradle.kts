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
    ":engine:logging"
)

// App modules -
include(
    ":engine:app:app-core",
    ":engine:app:app-desktop",
    ":engine:app:app-headless"
)

// Data modules -
include(
    ":engine:data:data-core",
    ":engine:data:data-saving",
)

// Misc modules -
include(
    ":engine:testkit",
    ":engine:utils"
)

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
