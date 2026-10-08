package io.canopy.tooling.compiler

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
            private val slots = mutableMapOf<String, Any?>()
            @Suppress("UNCHECKED_CAST")
            protected fun <T> compilerPropertyGet(key: String, default: T): T =
                if (slots.containsKey(key)) slots[key] as T else default
            protected fun <T> compilerPropertySet(key: String, value: T) { slots[key] = value }
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
            "class Enemy : Node<Enemy>() { lateinit var resource: String }",
            "class Enemy : Node<Enemy>() { val health by lazy { 100 } }",
            "class Enemy : Node<Enemy>() { var health by kotlin.properties.Delegates.observable(100) { _, _, _ -> } }",
            "class Enemy : Node<Enemy>() { @JvmField var health = 100 }",
            "class Enemy : Node<Enemy>() { @Volatile var health = 100 }",
            "class Enemy : Node<Enemy>() { @Transient var health = 100 }",
            "fun make() { var health = 100; class Enemy : Node<Enemy>() { fun damage() { health-- } } }",
            "class Outer { inner class Enemy : Node<Enemy>() }",
            "class Enemy : Node<Enemy>(), Runnable by (java.lang.Runnable { })",
            "fun make() { val retained = Any(); class Enemy : Node<Enemy>() { val payload = retained } }",
            "fun make() { val retained = Any(); val node = object : Node<Nothing>() { val payload = retained } }"
        ).forEach { declaration ->
            compileConsumer(declaration) { code, output ->
                assertEquals(ExitCode.COMPILATION_ERROR, code, declaration + output)
                assertTrue(
                    "CANOPY_UNMANAGED_NODE_STATE" in output || "CANOPY_UNSUPPORTED_NODE_PROPERTY" in output,
                    output
                )
                assertTrue("nodeProperty" in output, output)
                assertTrue(Regex("Game\\.kt:[0-9]+:[0-9]+").containsMatchIn(output), output)
            }
        }
    }

    @Test
    fun `automatic properties preserve constructors custom accessors overrides and defaults without payload fields`() {
        compileConsumer(
            """
            val events = mutableListOf<String>()
            open class Base<N : Base<N>>(value: Int) : Node<N>() {
                private var same = value.also { events += "base" }
                open val overridden: Int = 10
                val beforeOverride = overridden
                fun baseValue() = same
            }
            class Enemy(val constructorValue: Int) : Base<Enemy>(constructorValue) {
                private var same = constructorValue.also { events += "child" }
                override val overridden = 20
                var custom = constructorValue
                    get() = field + 1
                    set(value) { field = value * 2 }
                val earlier = same + constructorValue
                fun copyFrom(other: Enemy) { same = other.same }
                init { events += "init"; same += 1 }
                fun values() = listOf(baseValue(), same, beforeOverride, overridden, custom, earlier)
            }
            class Generic<T>(val payload: T) : Node<Generic<T>>()
            fun result(): String {
                val enemy = Enemy(5)
                check(enemy.values() == listOf(5, 6, 0, 20, 6, 10))
                enemy.custom = 3
                check(enemy.custom == 7)
                check(Generic("generic").payload == "generic")
                check(events == listOf("base", "child", "init"))
                val other = Enemy(7)
                enemy.copyFrom(other)
                check(enemy.values()[1] == 8)
                return "okay"
            }
            """.trimIndent(),
            inspectOutput = { output, apiOutput ->
                java.net.URLClassLoader(
                    arrayOf(output.toURI().toURL(), apiOutput.toURI().toURL()),
                    this.javaClass.classLoader
                ).use { loader ->
                    listOf("Base", "Enemy", "Generic").forEach { name ->
                        val payload = loader.loadClass(name).declaredFields.filter {
                            !java.lang.reflect.Modifier.isStatic(it.modifiers)
                        }
                        assertTrue(payload.isEmpty(), "$name retained fields: $payload")
                    }
                    assertEquals("okay", loader.loadClass("GameKt").getMethod("result").invoke(null))
                }
            }
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
    }

    @Test
    fun `missing runtime automatic storage ABI fails with precise compatibility diagnostic`() {
        listOf(
            """
                package io.canopy.engine.core.nodes
                abstract class Node<N : Node<N>>
            """.trimIndent(),
            api.replace("protected fun <T> compilerProperty", "private fun <T> compilerProperty"),
            api.replace("protected fun <T> compilerProperty", "protected open fun <T> compilerProperty")
        ).forEach { incompatibleApi ->
            compileConsumer(
                "class Enemy : Node<Enemy>() { var health = 100 }",
                apiSource = incompatibleApi
            ) { code, output ->
                assertEquals(ExitCode.COMPILATION_ERROR, code, output)
                assertTrue("CANOPY_NODE_PROPERTY_ABI" in output, output)
            }
        }
    }

    @Test
    fun `automatic properties preserve initializer failures and static no storage properties`() {
        compileConsumer(
            """
            var evaluations = 0
            class Enemy : Node<Enemy>() {
                val first = (++evaluations)
                val failed: String = error("initializer failed")
                val never = (++evaluations)
                val computed get() = first + 1
                companion object { const val CONSTANT = 5; var shared = 10 }
            }
            class Plain { var retained = "plain" }
            object Ordinary { var retained = "object" }
            fun result(): String {
                try { Enemy(); error("expected failure") }
                catch (failure: IllegalStateException) { check(failure.message == "initializer failed") }
                check(evaluations == 1)
                check(Enemy.CONSTANT == 5 && Enemy.shared == 10)
                check(Plain().retained == "plain" && Ordinary.retained == "object")
                return "okay"
            }
            """.trimIndent(),
            inspectOutput = { output, apiOutput ->
                java.net.URLClassLoader(
                    arrayOf(output.toURI().toURL(), apiOutput.toURI().toURL()),
                    this.javaClass.classLoader
                ).use { loader ->
                    assertEquals("okay", loader.loadClass("GameKt").getMethod("result").invoke(null))
                    assertTrue(loader.loadClass("Plain").declaredFields.any { it.name == "retained" })
                    assertTrue(
                        loader.loadClass("Enemy").declaredFields.none {
                            !java.lang.reflect.Modifier.isStatic(it.modifiers)
                        }
                    )
                }
            }
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
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
    fun `consumer typed dependency delegates and automatic descriptor storage compile`() {
        // Arrange / Act / Assert
        compileConsumer(
            """
            import io.canopy.engine.core.queries.child
            class Enemy : Node<Enemy>() { val target by child<Enemy>() }
            """.trimIndent()
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
        compileConsumer(
            """
            import io.canopy.engine.core.queries.child
            class Enemy : Node<Enemy>() { val query = child<Enemy>() }
            """.trimIndent()
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
    }

    @Test
    fun `global delegates compile in node and ordinary scopes alongside stored descriptors`() {
        // Arrange / Act / Assert
        compileConsumer(
            """
            import io.canopy.engine.core.queries.*
            class Service
            val shared by manager<Service>()
            object Services { val optional by managerOrNull<Service>() }
            class Controller { val service by manager<Service>() }
            class Enemy : Node<Enemy>() {
                val service by manager<Service>()
                val target by child<Enemy>()
            }
            fun local() { val service by manager<Service>() }
            """.trimIndent()
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
        compileConsumer(
            """
            import io.canopy.engine.core.queries.manager
            class Enemy : Node<Enemy>() { val descriptor = manager<Any>() }
            """.trimIndent()
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
    }

    @Test
    fun `node dependencies reject ordinary receivers and the shared base cannot delegate`() {
        // Arrange / Act / Assert
        listOf(
            "class Controller { val target by child<Enemy>() }",
            "val target by child<Enemy>()",
            "class Controller { val service by (manager<Any>() as Dependency<Any>) }"
        ).forEach { declaration ->
            compileConsumer(
                """
                import io.canopy.engine.core.queries.*
                class Enemy : Node<Enemy>()
                $declaration
                """.trimIndent()
            ) { code, _ -> assertEquals(ExitCode.COMPILATION_ERROR, code) }
        }
    }

    @Test
    fun `consumer asset delegates and automatic descriptors compile while non asset types fail`() {
        // Arrange / Act / Assert
        compileConsumer(
            """
            import io.canopy.engine.data.assets.*
            class Rules : CanopyAsset { override fun close() = Unit }
            class Level : Node<Level>() { val rules by asset<Rules>("rules") }
            """.trimIndent()
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
        compileConsumer(
            """
            import io.canopy.engine.data.assets.*
            class Rules : CanopyAsset { override fun close() = Unit }
            class Level : Node<Level>() { val rules = asset<Rules>("rules") }
            """.trimIndent()
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
        compileConsumer(
            """
            import io.canopy.engine.data.assets.*
            class Level : Node<Level>() { val rules by asset<String>("rules") }
            """.trimIndent()
        ) { code, output ->
            assertEquals(ExitCode.COMPILATION_ERROR, code, output)
            assertTrue("CanopyAsset" in output, output)
        }
    }

    @Test
    fun `registered rule provider runs alongside mandatory node checks`() {
        withRuleJar(TestNamingRule::class.java) { jar ->
            // Act / Assert: the provider contributes its diagnostic; mandatory safety remains active.
            compileConsumer("class Forbidden : Node<Forbidden>()", jar.path) { code, output ->
                assertEquals(ExitCode.COMPILATION_ERROR, code, output)
                assertTrue("CANOPY_TEST_NAMING" in output, output)
            }
            compileConsumer("class Enemy : Node<Enemy>() { lateinit var health: String }", jar.path) { code, output ->
                assertEquals(ExitCode.COMPILATION_ERROR, code, output)
                assertTrue(
                    "CANOPY_UNMANAGED_NODE_STATE" in output || "CANOPY_UNSUPPORTED_NODE_PROPERTY" in output,
                    output
                )
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
            compileConsumer("class Enemy : Node<Enemy>() { lateinit var health: String }", jar.path) { code, output ->
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
                output.putNextEntry(JarEntry("META-INF/services/io.canopy.tooling.compiler.CanopyCompilerRule"))
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
        apiSource: String = api,
        inspectOutput: ((java.io.File, java.io.File) -> Unit)? = null,
        assertResult: (ExitCode, String) -> Unit,
    ) {
        val directory = Files.createTempDirectory("canopy-consumer-compiler").toFile()
        try {
            val apiFile = directory.resolve("Api.kt").apply { writeText(apiSource) }
            val apiOutput = directory.resolve("api")
            val standardLibrary = java.io.File(
                kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI()
            ).path
            val bytes = ByteArrayOutputStream()
            val compiler = K2JVMCompiler()
            val common = arrayOf("-no-stdlib", "-no-reflect", "-classpath", standardLibrary)
            val queryFile = directory.resolve("Queries.kt").apply {
                writeText(
                    """
                    package io.canopy.engine.core.queries
                    import kotlin.reflect.KProperty
                    import io.canopy.engine.core.nodes.Node
                    sealed class Dependency<T>
                    class NodeDependency<T> : Dependency<T>() {
                        operator fun getValue(owner: Node<*>, property: KProperty<*>): T = TODO()
                    }
                    class GlobalDependency<T> : Dependency<T>() {
                        operator fun getValue(owner: Any?, property: KProperty<*>): T = TODO()
                    }
                    inline fun <reified T : Any> manager(): GlobalDependency<T> = GlobalDependency()
                    inline fun <reified T : Any> managerOrNull(): GlobalDependency<T?> = GlobalDependency()
                    inline fun <reified T : Node<*>> child(): NodeDependency<T> = NodeDependency()
                    """.trimIndent()
                )
            }
            val assetFile = directory.resolve("Assets.kt").apply {
                writeText(
                    """
                    package io.canopy.engine.data.assets
                    import kotlin.reflect.KProperty
                    import io.canopy.engine.core.nodes.Node
                    interface CanopyAsset : AutoCloseable
                    class AssetDelegate<T : CanopyAsset> {
                        operator fun getValue(owner: Node<*>, property: KProperty<*>): T = TODO()
                    }
                    inline fun <reified T : CanopyAsset> asset(path: String): AssetDelegate<T> = AssetDelegate()
                    """.trimIndent()
                )
            }
            val apiCode = compiler.exec(
                PrintStream(bytes),
                *common,
                "-d",
                apiOutput.path,
                apiFile.path,
                queryFile.path,
                assetFile.path
            )
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
            if (code == ExitCode.OK) inspectOutput?.invoke(directory.resolve("game"), apiOutput)
        } finally {
            directory.deleteRecursively()
        }
    }
}
