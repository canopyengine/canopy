package io.canopy.tooling.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File
import java.util.jar.JarFile

/** Verifies that the two execution hosts receive only their own entry points and service registrations. */
class ArtifactIsolationTests {
    @Test
    fun `compiler and Gradle artifacts keep their entry points isolated`() {
        JarFile(System.getProperty("canopy.compiler.jar")).use { compiler ->
            assertTrue(compiler.getJarEntry("io/canopy/tooling/compiler/CanopyCompilerRegistrar.class") != null)
            assertFalse(
                compiler.entries().asSequence().any {
                    it.name.startsWith("io/canopy/tooling/compiler/gradle/")
                }
            )
            assertFalse(compiler.entries().asSequence().any { it.name.startsWith("META-INF/gradle-plugins/") })
            val entry = compiler.getJarEntry(
                "META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar"
            )
            assertEquals(
                "io.canopy.tooling.compiler.CanopyCompilerRegistrar",
                compiler.getInputStream(entry).bufferedReader().use { it.readText().trim() }
            )
        }
        JarFile(System.getProperty("canopy.gradle.jar")).use { gradle ->
            assertTrue(gradle.getJarEntry("io/canopy/tooling/compiler/gradle/CanopyCompilerPlugin.class") != null)
            assertTrue(gradle.getJarEntry("META-INF/gradle-plugins/io.github.canopyengine.compiler.properties") != null)
            assertFalse(gradle.getJarEntry("io/canopy/tooling/compiler/CanopyCompilerRegistrar.class") != null)
            assertFalse(
                gradle.getJarEntry("META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar") !=
                    null
            )
        }
        val repository = File(System.getProperty("canopy.verification.repository"))
        val marker = repository.resolve("io/github/canopyengine/compiler").walkTopDown().single {
            it.name.endsWith(".pom") && "io.github.canopyengine.compiler.gradle.plugin" in it.name
        }
        assertTrue("<groupId>io.github.canopyengine</groupId>" in marker.readText())
        assertTrue("<artifactId>canopy-compiler-gradle</artifactId>" in marker.readText())
    }
}
