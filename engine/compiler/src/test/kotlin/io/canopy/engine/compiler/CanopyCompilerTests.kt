package io.canopy.engine.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler

/** Compiles consumer declarations and provider fixtures to verify safety rules, general traversal and diagnostics. */
class CanopyCompilerTests {
    private val api = """
        package io.canopy.engine.core.nodes
        import kotlin.reflect.KProperty
        abstract class Node<N : Node<N>> {
            protected fun <T> nodeProperty(initial: T): NodeProperty<T> = NodeProperty.create(initial)
        }
        class NodeProperty<T> private constructor(private var value: T) {
            companion object { fun <T> create(value: T) = NodeProperty(value) }
            operator fun getValue(receiver: Node<*>, property: KProperty<*>): T = value
            operator fun setValue(receiver: Node<*>, property: KProperty<*>, value: T) { this.value = value }
        }
    """.trimIndent()

    @Test
    fun `consumer node state declarations fail with actionable diagnostic`() {
        listOf(
            "class Enemy : Node<Enemy>() { var health = 100 }",
            "class Enemy : Node<Enemy>() { val health = 100 }",
            "class Enemy(val health: Int) : Node<Enemy>()",
            "class Enemy : Node<Enemy>() { lateinit var resource: String }",
            "class Enemy : Node<Enemy>() { val health by lazy { 100 } }",
            "class Enemy : Node<Enemy>() { var health by kotlin.properties.Delegates.observable(100) { _, _, _ -> } }",
            "class Enemy : Node<Enemy>() { @JvmField var health = 100 }",
            "fun make() { var health = 100; class Enemy : Node<Enemy>() { fun damage() { health-- } } }",
            "class Outer { inner class Enemy : Node<Enemy>() }",
            "class Enemy : Node<Enemy>(), Runnable by (java.lang.Runnable { })",
            "open class Base<N : Base<N>> : Node<N>() { var health = 100 }; class Enemy : Base<Enemy>()"
        ).forEach { declaration ->
            compileConsumer(declaration) { code, output ->
                assertEquals(ExitCode.COMPILATION_ERROR, code, declaration + output)
                assertTrue("CANOPY_UNMANAGED_NODE_STATE" in output, output)
                assertTrue("nodeProperty" in output, output)
                assertTrue(Regex("Game\\.kt:[0-9]+:[0-9]+").containsMatchIn(output), output)
            }
        }
    }

    @Test
    fun `consumer managed state computed properties and companions compile`() {
        compileConsumer(
            """
            open class Base<N : Base<N>> : Node<N>() { var health by nodeProperty(100) }
            class Enemy : Base<Enemy>() {
                val alive get() = health > 0
                var resource by nodeProperty<String?>(null)
                companion object { const val MAX_HEALTH = 100; var shared = "shared" }
            }
            """.trimIndent()
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
    }

    @Test
    fun `registered rule provider runs alongside mandatory node checks`() {
        withRuleJar(TestNamingRule::class.java) { jar ->
            // Act / Assert: the provider contributes its diagnostic; mandatory safety remains active.
            compileConsumer("class Forbidden : Node<Forbidden>()", jar.path) { code, output ->
                assertEquals(ExitCode.COMPILATION_ERROR, code, output)
                assertTrue("CANOPY_TEST_NAMING" in output, output)
            }
            compileConsumer("class Enemy : Node<Enemy>() { var health = 100 }", jar.path) { code, output ->
                assertEquals(ExitCode.COMPILATION_ERROR, code, output)
                assertTrue("CANOPY_UNMANAGED_NODE_STATE" in output, output)
            }
            compileConsumer("class Enemy : Node<Enemy>() { var health by nodeProperty(100) }", jar.path) {
                    code,
                    output,
                ->
                assertEquals(ExitCode.OK, code, output)
            }
        }
    }

    @Test
    fun `general rules check non node top level and nested declarations exactly once`() {
        withRuleJar(TestNamingRule::class.java) { jar ->
            compileConsumer(
                """
                fun ForbiddenFunction() {}
                class Container {
                    fun ForbiddenMember() {}
                    val ForbiddenProperty = 1
                    class ForbiddenType
                }
                """.trimIndent(),
                jar.path
            ) { code, output ->
                assertEquals(ExitCode.COMPILATION_ERROR, code, output)
                assertEquals(4, Regex("CANOPY_TEST_NAMING").findAll(output).count(), output)
                assertTrue(Regex("Game\\.kt:[0-9]+:[0-9]+").containsMatchIn(output), output)
            }
        }
    }

    @Test
    fun `rule providers cannot replace mandatory safety through duplicate IDs`() {
        withRuleJar(TestDuplicateRule::class.java) { jar ->
            compileConsumer("class Enemy : Node<Enemy>() { var health = 100 }", jar.path) { code, output ->
                assertTrue(code != ExitCode.OK, output)
                assertTrue("Canopy compiler rule IDs must be unique" in output, output)
            }
        }
    }

    private fun withRuleJar(provider: Class<out CanopyCompilerRule>, block: (java.io.File) -> Unit) {
        // Package a separate provider without changing registrar or traversal source.
        val jar = Files.createTempFile("canopy-extra-rule", ".jar").toFile()
        try {
            JarOutputStream(jar.outputStream()).use { output ->
                JarFile(System.getProperty("canopy.compiler.jar")).use { compiler ->
                    compiler.entries().asSequence().forEach { entry ->
                        output.putNextEntry(JarEntry(entry.name))
                        if (!entry.isDirectory) compiler.getInputStream(entry).use { it.copyTo(output) }
                        output.closeEntry()
                    }
                }
                val classPath = provider.name.replace('.', '/') + ".class"
                output.putNextEntry(JarEntry(classPath))
                provider.classLoader.getResourceAsStream(classPath)!!.use { it.copyTo(output) }
                output.closeEntry()
                output.putNextEntry(JarEntry("META-INF/services/io.canopy.engine.compiler.CanopyCompilerRule"))
                output.write((provider.name + "\n").toByteArray())
                output.closeEntry()
            }
            block(jar)
        } finally {
            jar.delete()
        }
    }

    private fun compileConsumer(
        source: String,
        additionalPlugin: String? = null,
        assertResult: (ExitCode, String) -> Unit,
    ) {
        val directory = Files.createTempDirectory("canopy-consumer-compiler").toFile()
        try {
            val apiFile = directory.resolve("Api.kt").apply { writeText(api) }
            val apiOutput = directory.resolve("api")
            val standardLibrary = java.io.File(
                kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI()
            ).path
            val bytes = ByteArrayOutputStream()
            val compiler = K2JVMCompiler()
            val common = arrayOf("-no-stdlib", "-no-reflect", "-classpath", standardLibrary)
            val apiCode = compiler.exec(PrintStream(bytes), *common, "-d", apiOutput.path, apiFile.path)
            assertEquals(ExitCode.OK, apiCode, bytes.toString())
            bytes.reset()
            val game = directory.resolve("Game.kt").apply {
                writeText("import io.canopy.engine.core.nodes.*\n" + source)
            }
            val arguments = arrayOf(
                "-no-stdlib",
                "-no-reflect",
                "-classpath",
                standardLibrary + java.io.File.pathSeparator + apiOutput.path,
                "-Xplugin=" +
                    (additionalPlugin ?: System.getProperty("canopy.compiler.jar")),
                "-d",
                directory.resolve("game").path,
                game.path
            )
            val code = if (additionalPlugin == null) {
                compiler.exec(PrintStream(bytes), *arguments)
            } else {
                // Isolate plugin loading from the test worker, which already loads our main classes.
                val executable = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
                val java = java.io.File(System.getProperty("java.home"), "bin/$executable").path
                val process = ProcessBuilder(
                    listOf(
                        java,
                        "-cp",
                        System.getProperty("canopy.compiler.runtime"),
                        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler"
                    ) + arguments
                ).redirectErrorStream(true).start()
                process.inputStream.use { it.copyTo(bytes) }
                val result = process.waitFor()
                ExitCode.entries.first { it.code == result }
            }
            assertResult(code, bytes.toString())
        } finally {
            directory.deleteRecursively()
        }
    }
}
