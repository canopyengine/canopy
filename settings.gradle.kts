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
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")


/* ===================================== */
/*      Canopy Modules Dependencies      */
/* ===================================== */

// Root modules
include(":engine:core")
include(":engine:input")
// include(":engine:graphics")
// include(":engine:physics")
include(":engine:logging")

// App modules
include(":engine:app:app-core")
// include(":engine:app:app-desktop")
include(":engine:app:app-terminal")
include(":engine:app:app-test")

// Data modules
include(":engine:data:data-core")
include(":engine:data:data-saving")

// Misc modules
include(":engine:utils")
