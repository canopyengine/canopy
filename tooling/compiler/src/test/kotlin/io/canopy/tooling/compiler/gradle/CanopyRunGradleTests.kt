package io.canopy.tooling.compiler.gradle

import kotlin.test.*
import java.nio.file.Files
import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner

/** Verifies the published plugin from a consumer with no copied launcher and no wrapper invocation. */
class CanopyRunGradleTests {
    @Test
    fun `bundled launch preserves run configuration and exit status with configuration cache`() {
        val directory = Files.createTempDirectory("canopy bundled game ").toFile()
        try {
            java.io.File(System.getProperty("canopy.verification.repository"))
                .copyRecursively(directory.resolve("repository"))
            directory.resolve("settings.gradle.kts").writeText(
                """
                pluginManagement { repositories { maven { url = uri("repository") }; gradlePluginPortal(); mavenCentral() } }
                rootProject.name = "bundled-game"
                """.trimIndent()
            )
            directory.resolve("game directory").mkdirs()
            directory.resolve("build.gradle.kts").writeText(
                """
                plugins {
                    id("org.jetbrains.kotlin.jvm") version "$CANOPY_KOTLIN_VERSION"
                    id("io.github.canopyengine.compiler") version "$CANOPY_VERSION"
                    application
                }
                repositories { maven { url = uri("repository") }; mavenCentral() }
                kotlin { jvmToolchain(17) }
                application { mainClass.set("Game"); applicationDefaultJvmArgs = listOf("-Dgame.flag=two words") }
                tasks.named<JavaExec>("run") {
                    workingDir = file("game directory")
                    systemProperty("run.flag", "configured")
                    environment("CANOPY_GAME_FIXTURE", "environment value")
                }
                tasks.named<Sync>("installDist") { into(layout.buildDirectory.dir("custom install")) }
                """.trimIndent()
            )
            directory.resolve("src/main/java/Game.java").apply {
                parentFile.mkdirs()
                writeText(
                    """
                    public class Game {
                        public static void main(String[] args) {
                            System.out.println("GAME=" + System.getProperty("game.flag"));
                            System.out.println("RUN=" + System.getProperty("run.flag"));
                            System.out.println("ENV=" + System.getenv("CANOPY_GAME_FIXTURE"));
                            System.out.println("DIR=" + System.getProperty("user.dir"));
                            for (String arg : args) System.out.println("ARG=" + arg);
                            if (args.length > 0 && args[0].equals("fail")) System.exit(7);
                        }
                    }
                    """.trimIndent()
                )
            }
            val runner = GradleRunner.create().withProjectDir(directory)
                .withTestKitDir(java.io.File(System.getProperty("canopy.gradle.home")))
            val arguments = listOf(
                "canopyRun",
                "--current-terminal",
                "--args=one 'two words'",
                "--configuration-cache",
                "--max-workers=2",
                "--console=plain",
                "--stacktrace"
            )

            // Act: the consumer has no Gradle wrapper and no source launcher; the plugin supplies everything.
            val first = runner.withArguments(arguments).build()
            val second = runner.withArguments(arguments).build()

            // Assert
            assertEquals(1, Regex("GAME=two words").findAll(first.output).count(), first.output)
            assertTrue(first.output.contains("RUN=configured"), first.output)
            assertTrue(first.output.contains("ENV=environment value"), first.output)
            val actualDirectory = first.output.lineSequence().first { it.startsWith("DIR=") }.removePrefix("DIR=")
            assertTrue(
                Files.isSameFile(directory.resolve("game directory").toPath(), Path.of(actualDirectory)),
                first.output
            )
            assertTrue(first.output.contains("ARG=one"), first.output)
            assertTrue(first.output.contains("ARG=two words"), first.output)
            assertTrue(second.output.contains("Reusing configuration cache"), second.output)
            assertFalse(directory.resolve("build/canopy/launcher/launch.bin").exists())
            val failure = runner.withArguments(
                "canopyRun",
                "--current-terminal",
                "--args=fail",
                "--max-workers=2",
                "--console=plain"
            ).buildAndFail()
            assertTrue(failure.output.contains("exit value 7"), failure.output)
        } finally {
            directory.deleteRecursively()
        }
    }
}
