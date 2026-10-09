package io.canopy.tooling.compiler.gradle

import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Files
import org.gradle.testkit.runner.GradleRunner

/** Exercises the public plugin from a separate game build, including its test compilation. */
class ConsumerGradleTests {
    @Test
    fun `plugin transforms and executes consumer state while rejecting unsupported main and test declarations`() {
        val directory = Files.createTempDirectory("canopy-game-build").toFile()
        try {
            java.io.File(System.getProperty("canopy.verification.repository"))
                .copyRecursively(directory.resolve("repository"))
            directory.resolve("settings.gradle.kts").writeText(
                """
                pluginManagement { repositories { maven { url = uri("repository") }; gradlePluginPortal(); mavenCentral() } }
                rootProject.name = "consumer-game"
                """.trimIndent()
            )
            directory.resolve("build.gradle.kts").writeText(
                """
                plugins {
                    id("org.jetbrains.kotlin.jvm") version "$CANOPY_KOTLIN_VERSION"
                    id("io.github.canopyengine.compiler") version "$CANOPY_VERSION"
                }
                repositories { maven { url = uri("repository") }; mavenCentral() }
                kotlin { jvmToolchain(17) }
                tasks.register<JavaExec>("runConsumer") {
                    classpath = sourceSets.main.get().runtimeClasspath
                    mainClass.set("EnemyNodeKt")
                }
                """.trimIndent()
            )
            val sources = directory.resolve("src/main/kotlin").apply { mkdirs() }
            sources.resolve("Node.kt").writeText(
                """
                package io.canopy.engine.core.nodes
                import kotlin.reflect.KProperty
                var constructionDepth = 0
                inline fun <T> nodeConstruction(factory: () -> T): T {
                    constructionDepth++
                    try { return factory() } finally { constructionDepth-- }
                }
                abstract class Node<N : Node<N>> {
                    init { check(constructionDepth > 0) }
                    private val slots = mutableMapOf<String, Any?>()
                    @Suppress("UNCHECKED_CAST")
                    protected fun <T> compilerPropertyGet(key: String, default: T): T =
                        if (slots.containsKey(key)) slots[key] as T else default
                    protected fun <T> compilerPropertySet(key: String, value: T) { slots[key] = value }
                    protected fun <T> nodeProperty(value: T): NodeProperty<T> = NodeProperty(value)
                }
                class NodeProperty<T>(private var value: T) {
                    operator fun getValue(node: Node<*>, property: KProperty<*>): T = value
                    operator fun setValue(node: Node<*>, property: KProperty<*>, value: T) { this.value = value }
                }
                """.trimIndent()
            )
            val game = sources.resolve("EnemyNode.kt")
            game.writeText(
                """
                import io.canopy.engine.core.nodes.Node
                class EnemyNode : Node<EnemyNode>() { @JvmField var health = 100 }
                """.trimIndent()
            )
            val runner = GradleRunner.create().withProjectDir(directory)
            val unsafeMain = runner.withArguments(
                "compileKotlin",
                "--offline",
                "--stacktrace",
                "--gradle-user-home",
                System.getProperty("canopy.gradle.home")
            ).buildAndFail()
            assertTrue("CANOPY_UNMANAGED_NODE_STATE" in unsafeMain.output, unsafeMain.output)

            game.writeText(
                game.readText().replace("@JvmField var health = 100", "var health = 100") +
                    """

                    fun main() {
                        val enemy = EnemyNode()
                        check(enemy.health == 100)
                        enemy.health -= 1
                        check(enemy.health == 99)
                        check(EnemyNode::class.java.declaredFields.none {
                            !java.lang.reflect.Modifier.isStatic(it.modifiers)
                        })
                        java.io.File("verified.txt").writeText("automatic state works")
                    }
                    """.trimIndent()
            )
            runner.withArguments(
                "runConsumer",
                "--offline",
                "--stacktrace",
                "--gradle-user-home",
                System.getProperty("canopy.gradle.home")
            ).build()
            assertTrue(directory.resolve("verified.txt").readText() == "automatic state works")
            directory.resolve("build.gradle.kts").appendText(
                """

                tasks.register<JavaExec>("verifyMainConstruction") {
                    classpath = sourceSets["main"].runtimeClasspath
                    mainClass.set("EnemyNodeKt")
                }
                tasks.register<JavaExec>("verifyTestConstruction") {
                    classpath = sourceSets["test"].runtimeClasspath
                    mainClass.set("SafeTestNodeKt")
                }
                """.trimIndent().let {
                    "\n" + it
                }
            )
            val tests = directory.resolve("src/test/kotlin").apply { mkdirs() }
            tests.resolve("UnsafeTestNode.kt").writeText(
                """
                import io.canopy.engine.core.nodes.Node
                class UnsafeTestNode : Node<UnsafeTestNode>() { lateinit var retained: Any }
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
            tests.resolve("UnsafeTestNode.kt").delete()
            tests.resolve("SafeTestNode.kt").writeText(
                """
                import io.canopy.engine.core.nodes.Node
                class SafeTestNode : Node<SafeTestNode>() { val retained = Any() }
                fun main() { SafeTestNode() }
                """.trimIndent()
            )
            runner.withArguments(
                "verifyMainConstruction",
                "verifyTestConstruction",
                "--offline",
                "--stacktrace",
                "--gradle-user-home",
                System.getProperty("canopy.gradle.home")
            ).build()
        } finally {
            directory.deleteRecursively()
        }
    }
}
