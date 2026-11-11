package io.canopy.tooling.compiler.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import io.canopy.tooling.compiler.TestFactsProvider
import io.canopy.tooling.compiler.TestSecondProvider
import org.gradle.testkit.runner.GradleRunner

class RuleProviderGradleTests {
    @Test
    fun `separate provider jar reports warnings errors and failures without entering game runtime`() {
        // Arrange: a genuine additional JAR, containing only providers and their service entry.
        val directory = Files.createTempDirectory("canopy-provider-game").toFile()
        try {
            java.io.File(System.getProperty("canopy.verification.repository"))
                .copyRecursively(directory.resolve("repository"))
            val jar = directory.resolve("studio-rules.jar")
            val providers = listOf(TestFactsProvider::class.java, TestSecondProvider::class.java)
            JarOutputStream(jar.outputStream()).use { output ->
                providers.forEach { provider ->
                    val path = provider.name.replace('.', '/') + ".class"
                    output.putNextEntry(JarEntry(path))
                    provider.classLoader.getResourceAsStream(path)!!.use { it.copyTo(output) }
                    output.closeEntry()
                }
                output.putNextEntry(JarEntry("META-INF/services/io.canopy.tooling.compiler.rules.RuleProvider"))
                output.write(providers.joinToString("\n", postfix = "\n") { it.name }.toByteArray())
                output.closeEntry()
            }
            JarFile(jar).use { assertEquals(3, it.size()) }
            directory.resolve("settings.gradle.kts").writeText(
                """
                pluginManagement { repositories { maven { url = uri("repository") }; gradlePluginPortal(); mavenCentral() } }
                rootProject.name = "provider-game"
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
                dependencies { canopyCompilerRules(files("studio-rules.jar")) }
                tasks.register<JavaExec>("runGame") {
                    classpath = sourceSets.main.get().runtimeClasspath
                    mainClass.set("GameKt")
                }
                tasks.register("verifyIsolation") {
                    dependsOn("runGame")
                    doLast {
                        check(configurations["runtimeClasspath"].files.none { it.name == "studio-rules.jar" })
                        check(configurations["testRuntimeClasspath"].files.none { it.name == "studio-rules.jar" })
                    }
                }
                """.trimIndent()
            )
            val sources = directory.resolve("src/main/kotlin").apply { mkdirs() }
            sources.resolve("Node.kt").writeText(
                """
                package io.canopy.engine.core.nodes
                inline fun <T> nodeConstruction(factory: () -> T): T = factory()
                abstract class Node<N : Node<N>> {
                    private val slots = mutableMapOf<String, Any?>()
                    @Suppress("UNCHECKED_CAST")
                    protected fun <T> compilerPropertyGet(key: String, default: T): T =
                        if (slots.containsKey(key)) slots[key] as T else default
                    protected fun <T> compilerPropertySet(key: String, value: T) { slots[key] = value }
                }
                """.trimIndent()
            )
            val game = sources.resolve("Game.kt")
            val runner = GradleRunner.create().withProjectDir(directory).withArguments(
                "verifyIsolation",
                "--offline",
                "--console=plain",
                "--gradle-user-home",
                System.getProperty("canopy.gradle.home")
            )

            // Act / Assert: a warning is visible but permits successful bytecode generation.
            game.writeText(
                """
                import io.canopy.engine.core.nodes.Node
                class Warn : Node<Warn>() { var health = 3 }
                fun main() {
                    val node = Warn()
                    check(node.health == 3)
                    node.health++
                    check(node.health == 4)
                    check(Warn::class.java.declaredFields.none { !java.lang.reflect.Modifier.isStatic(it.modifiers) })
                }
                """.trimIndent()
            )
            val warning = runner.build().output
            assertTrue("TEST_NAME_WARNING" in warning, warning)
            assertTrue("w:" in warning, warning)
            assertTrue(directory.resolve("build/classes/kotlin/main/Warn.class").exists())

            // Multiple independent errors are retained in the same validation phase.
            game.writeText("class Forbidden")
            val errors = runner.buildAndFail().output
            assertTrue("TEST_NAME_ERROR" in errors, errors)
            assertTrue("TEST_SECOND_CHECK" in errors, errors)
            assertFalse(directory.resolve("build/classes/kotlin/main/Forbidden.class").exists())

            // Invalid reporting cannot invent a diagnostic or change its registered severity.
            game.writeText("class Undeclared")
            val undeclared = runner.buildAndFail().output
            assertTrue("CANOPY_RULE_PROVIDER" in undeclared, undeclared)
            assertFalse("[NOT_DECLARED]:" in undeclared, undeclared)

            // A broken provider does not hide another provider or mandatory storage checks.
            game.writeText(
                "import io.canopy.engine.core.nodes.Node\n" +
                    "class Crash; class Bad : Node<Bad>() { lateinit var retained: Any }"
            )
            val broken = runner.buildAndFail().output
            assertTrue("CANOPY_RULE_PROVIDER" in broken, broken)
            assertTrue("TEST_SECOND_CHECK" in broken, broken)
            assertTrue("CANOPY_UNSUPPORTED_NODE_PROPERTY" in broken, broken)
            assertEquals(1, Regex("\\[CANOPY_RULE_PROVIDER]").findAll(broken).count(), broken)

            // A subsequent compilation gets a fresh provider and reporting context.
            sources.resolve("Node.kt").delete()
            game.writeText("class Safe; fun main() {}")
            runner.build()

            // Rule dependencies apply to tests too, without becoming test runtime dependencies.
            directory.resolve("src/test/kotlin").apply { mkdirs() }
                .resolve("RuleTest.kt").writeText("class Forbidden")
            val testError = runner.withArguments(
                "compileTestKotlin",
                "--offline",
                "--console=plain",
                "--gradle-user-home",
                System.getProperty("canopy.gradle.home")
            ).buildAndFail().output
            assertTrue("TEST_NAME_ERROR" in testError, testError)
        } finally {
            directory.deleteRecursively()
        }
    }
}
