package io.canopy.tooling.nodes

import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Files
import org.gradle.testkit.runner.GradleRunner

/** Exercises the public plugin from a separate game build, including its test compilation. */
class ConsumerGradleTests {
    @Test
    fun `plugin automatically checks consumer main and test node declarations`() {
        val directory = Files.createTempDirectory("canopy-game-build").toFile()
        try {
            val repository = directory.resolve("repository/io/canopy/engine-compiler/$CANOPY_VERSION")
            repository.mkdirs()
            java.io.File(System.getProperty("canopy.compiler.jar"))
                .copyTo(repository.resolve("engine-compiler-$CANOPY_VERSION.jar"))
            repository.resolve("engine-compiler-$CANOPY_VERSION.pom").writeText(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>io.canopy</groupId>
                <artifactId>engine-compiler</artifactId><version>$CANOPY_VERSION</version></project>
                """.trimIndent()
            )
            directory.resolve("settings.gradle.kts").writeText("rootProject.name = \"consumer-game\"")
            directory.resolve("build.gradle.kts").writeText(
                """
                plugins {
                    id("org.jetbrains.kotlin.jvm") version "$CANOPY_KOTLIN_VERSION"
                    id("io.canopy.node-state")
                }
                repositories { maven { url = uri("repository") }; mavenCentral() }
                kotlin { jvmToolchain(17) }
                """.trimIndent()
            )
            val sources = directory.resolve("src/main/kotlin").apply { mkdirs() }
            sources.resolve("Node.kt").writeText(
                """
                package io.canopy.engine.core.nodes
                import kotlin.reflect.KProperty
                abstract class Node<N : Node<N>> {
                    protected fun <T> nodeProperty(value: T): NodeProperty<T> = TODO()
                }
                class NodeProperty<T> {
                    operator fun getValue(node: Node<*>, property: KProperty<*>): T = TODO()
                    operator fun setValue(node: Node<*>, property: KProperty<*>, value: T) = Unit
                }
                """.trimIndent()
            )
            val game = sources.resolve("EnemyNode.kt")
            game.writeText(
                """
                import io.canopy.engine.core.nodes.Node
                class EnemyNode : Node<EnemyNode>() { var health = 100 }
                """.trimIndent()
            )
            val runner = GradleRunner.create().withProjectDir(directory).withPluginClasspath()
            runner.withPluginClasspath(
                runner.pluginClasspath + System.getProperty("canopy.kotlin.plugin.classpath")
                    .split(java.io.File.pathSeparator).map { java.io.File(it) }
            )
            val unsafeMain = runner.withArguments(
                "compileKotlin",
                "--offline",
                "--stacktrace",
                "--gradle-user-home",
                System.getProperty("canopy.gradle.home")
            ).buildAndFail()
            assertTrue("CANOPY_UNMANAGED_NODE_STATE" in unsafeMain.output, unsafeMain.output)

            game.writeText(game.readText().replace("var health = 100", "var health by nodeProperty(100)"))
            runner.withArguments(
                "compileKotlin",
                "--offline",
                "--stacktrace",
                "--gradle-user-home",
                System.getProperty("canopy.gradle.home")
            ).build()
            val tests = directory.resolve("src/test/kotlin").apply { mkdirs() }
            tests.resolve("UnsafeTestNode.kt").writeText(
                """
                import io.canopy.engine.core.nodes.Node
                class UnsafeTestNode : Node<UnsafeTestNode>() { val retained = Any() }
                """.trimIndent()
            )
            val unsafeTest = runner.withArguments(
                "compileTestKotlin",
                "--offline",
                "--stacktrace",
                "--gradle-user-home",
                System.getProperty("canopy.gradle.home")
            ).buildAndFail()
            assertTrue("CANOPY_UNMANAGED_NODE_STATE" in unsafeTest.output, unsafeTest.output)
        } finally {
            directory.deleteRecursively()
        }
    }
}
