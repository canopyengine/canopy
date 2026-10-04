pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}
rootProject.name = "node-gradle-plugin"
include(":engine-compiler")
project(":engine-compiler").projectDir = file("../../engine/compiler")
