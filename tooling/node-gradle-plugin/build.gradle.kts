import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `java-gradle-plugin`
    `maven-publish`
    jacoco
}
val canopyProperties = Properties().apply { file("../../gradle.properties").inputStream().use(::load) }
group = "io.canopy"
version = canopyProperties.getProperty("canopyVersion").trim()
kotlin { jvmToolchain(17) }
dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.gradle.plugin)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
tasks.test {
    useJUnitPlatform()
    systemProperty("canopy.gradle.home", gradle.gradleUserHomeDir.absolutePath)
    doFirst {
        systemProperty(
            "canopy.kotlin.plugin.classpath",
            classpath.filter {
                it.name.startsWith("kotlin-") ||
                    it.name.startsWith("kotlinx-") ||
                    it.name.startsWith("fus-") ||
                    it.name.startsWith("annotations-")
            }.asPath
        )
    }
    val compilerJar = project(":engine-compiler").tasks.named<Jar>("jar")
    dependsOn(compilerJar)
    systemProperty("canopy.compiler.jar", compilerJar.get().archiveFile.get().asFile.absolutePath)
}
gradlePlugin {
    plugins {
        create("nodeState") {
            id = "io.canopy.node-state"
            implementationClass = "io.canopy.tooling.nodes.CanopyNodePlugin"
            displayName = "Canopy node state validation"
            description = "Rejects unmanaged state in custom Canopy nodes during Kotlin compilation."
        }
    }
}
val coordinates = tasks.register("generateCoordinates") {
    val directory = layout.buildDirectory.dir("generated/coordinates")
    outputs.dir(directory)
    inputs.property("version", project.version.toString())
    inputs.property("kotlinVersion", libs.versions.kotlin.get())
    doLast {
        directory.get().file("io/canopy/tooling/nodes/Coordinates.kt").asFile.apply {
            parentFile.mkdirs()
            writeText(
                """package io.canopy.tooling.nodes

internal const val CANOPY_VERSION = "${project.version}"
internal const val CANOPY_KOTLIN_VERSION = "${libs.versions.kotlin.get()}"
"""
            )
        }
    }
}
kotlin.sourceSets.main { kotlin.srcDir(coordinates) }
tasks.named("compileKotlin") { dependsOn(coordinates) }

publishing { repositories { mavenLocal() } }
