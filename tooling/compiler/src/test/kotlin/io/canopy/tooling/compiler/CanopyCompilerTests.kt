package io.canopy.tooling.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance

/** Compiles consumer declarations and provider fixtures to verify safety rules, general traversal and diagnostics. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CanopyCompilerTests {
    private val fixtureDirectory = Files.createTempDirectory("canopy-compiler-api").toFile()
    private var defaultApiOutput: java.io.File? = null
    private val standardLibrary = java.io.File(
        kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI()
    ).path

    @AfterAll
    fun cleanupFixture() {
        fixtureDirectory.deleteRecursively()
    }

    private val api = """
        package io.canopy.engine.core.nodes
        import kotlin.reflect.KProperty
        var constructionDepth = 0
        var boundaries = 0
        inline fun <T> nodeConstruction(factory: () -> T): T {
            boundaries++
            constructionDepth++
            try { return factory() } finally { constructionDepth-- }
        }
        abstract class Node<N : Node<N>> {
            private val slots = mutableMapOf<String, Any?>()
            @Suppress("UNCHECKED_CAST")
            protected fun <T> compilerPropertyGet(key: String, default: T): T =
                if (slots.containsKey(key)) slots[key] as T else default
            protected fun <T> compilerPropertySet(key: String, value: T) { slots[key] = value }
            protected fun <T> nodeProperty(initial: T): NodeProperty<T> = NodeProperty.create(initial)
            protected inline fun <T> compilerOwned(block: () -> T): T = block()
            fun <T> signal(initial: T) = io.canopy.engine.core.flows.events.Signal(initial)
            fun <T> computed(block: () -> T) = io.canopy.engine.core.flows.events.Computed(block)
        }
        class NodeProperty<T> private constructor(private var value: T) {
            companion object { fun <T> create(value: T) = NodeProperty(value) }
            operator fun getValue(receiver: Node<*>, property: KProperty<*>): T = value
            operator fun setValue(receiver: Node<*>, property: KProperty<*>, value: T) { this.value = value }
        }
    """.trimIndent()

    private val appApi = """
        package io.canopy.engine.app
        open class App {
            var starts = 0
            var received = emptyList<String>()
            fun launch(vararg args: String): Unit = error("CANOPY_COMPILER_REQUIRED")
            fun launchAsync(threadName: String = "canopy-app", vararg args: String): String =
                error("CANOPY_COMPILER_REQUIRED")
            fun compilerLaunch(vararg args: String) { starts++; received = args.toList() }
            fun compilerLaunchAsync(threadName: String = "canopy-app", vararg args: String): String {
                compilerLaunch(*args)
                return threadName
            }
        }
    """.trimIndent()

    @Test
    fun `processed startup preserves defaults varargs references and single receiver evaluation`() {
        compileConsumer(
            """
            import io.canopy.engine.app.App
            class Game : App()
            fun verify(): Boolean {
                val app = Game()
                var evaluated = 0
                fun receiver(): Game { evaluated++; return app }
                receiver().launch("first")
                val bound = app::launch
                bound(arrayOf("second"))
                val unbound = App::launch
                unbound(app, arrayOf("third"))
                val name = app.launchAsync(args = arrayOf("last"))
                return evaluated == 1 && app.starts == 4 && app.received == listOf("last") && name == "canopy-app"
            }
            """.trimIndent(),
            execute = { loader -> assertEquals(true, loader.loadClass("GameKt").getMethod("verify").invoke(null)) }
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
    }

    @Test
    fun `unprocessed startup still compiles but reports the missing compiler at execution`() {
        compileConsumer(
            """
            import io.canopy.engine.app.App
            fun verify(): Boolean = try {
                App().launch()
                false
            } catch (failure: IllegalStateException) {
                failure.message == "CANOPY_COMPILER_REQUIRED"
            }
            """.trimIndent(),
            withPlugin = false,
            execute = { loader -> assertEquals(true, loader.loadClass("GameKt").getMethod("verify").invoke(null)) }
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
    }

    @Test
    fun `old runtime without startup helpers receives one compatibility diagnostic`() {
        compileConsumer(
            "import io.canopy.engine.app.App; fun start(app: App) { app.launch(); app.launchAsync() }",
            appApiSource = appApi.replace("compilerLaunch", "oldLaunch")
        ) { code, output ->
            assertEquals(ExitCode.COMPILATION_ERROR, code, output)
            assertEquals(1, Regex("\\[CANOPY_APP_ABI]").findAll(output).count(), output)
            assertTrue("Engine and compiler builds are incompatible" in output, output)
        }
    }

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
                assertTrue("help:" in output, output)
                assertTrue(Regex("Game\\.kt:[0-9]+:[0-9]+").containsMatchIn(output), output)
            }
        }
    }

    @Test
    fun `field annotation guidance fixes the declaration without requiring a delegate`() {
        // Arrange
        val wrong = "class Fish : Node<Fish>() { @JvmField var hunger = 100 }"

        // Act / Assert
        compileConsumer(wrong) { code, output ->
            assertEquals(ExitCode.COMPILATION_ERROR, code, output)
            assertTrue(
                "Remove the field annotation; ordinary Node properties are stored automatically." in output,
                output
            )
            assertEquals(1, Regex("\\[CANOPY_UNSUPPORTED_NODE_PROPERTY]").findAll(output).count(), output)
            assertTrue("CANOPY_UNMANAGED_NODE_STATE" !in output, output)
        }
        compileConsumer(wrong.replace("@JvmField ", "")) { code, output ->
            assertEquals(ExitCode.OK, code, output)
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
    fun `native reactive properties use Kotlin typing and reject accidental value initializers`() {
        // Arrange / Act / Assert
        compileConsumer(
            """
            class Fish : Node<Fish>() {
                var hunger by signal(100)
                    private set
                val hungry by computed { hunger < 20 }
                fun swim() { hunger -= 50 }
            }
            fun verify(): Boolean {
                val fish = Fish()
                check(!fish.hungry)
                fish.swim()
                fish.swim()
                return fish.hungry && fish.hunger == 0
            }
            """.trimIndent(),
            execute = { loader -> assertEquals(true, loader.loadClass("GameKt").getMethod("verify").invoke(null)) }
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
        listOf(
            "class Fish : Node<Fish>() { val hunger = signal(100) }",
            "class Fish : Node<Fish>() { val hungry = computed { true } }",
            "import io.canopy.engine.core.flows.events.signal as state; " +
                "class Fish : Node<Fish>() { val hunger = state(100) }"
        ).forEach { declaration ->
            compileConsumer(declaration) { code, output ->
                assertEquals(ExitCode.COMPILATION_ERROR, code, declaration + output)
                assertTrue("CANOPY_PROPERTY_DELEGATION" in output, output)
            }
        }
        compileConsumer(
            """
            import io.canopy.engine.core.flows.events.Signal
            class Fish : Node<Fish>() { val source = Signal(100) }
            fun local() { val source = io.canopy.engine.core.flows.events.signal(1) }
            """.trimIndent()
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
    }

    @Test
    fun `delegation errors highlight the assignment token and propose a compiling edit`() {
        val wrong = "class Aquarium : Node<Aquarium>() { val fishNum = computed { 1 } }"
        compileConsumer(wrong) { code, output ->
            assertEquals(ExitCode.COMPILATION_ERROR, code, output)
            assertTrue("[CANOPY_PROPERTY_DELEGATION]: Use `by` for a computed property" in output, output)
            assertTrue("help: Replace `=` with `by`" in output, output)
            assertTrue("2 - $wrong" in output, output)
            assertTrue("2 + ${wrong.replace("fishNum =", "fishNum by")}" in output, output)
            assertTrue("-> Game.kt:2:${wrong.indexOf('=') + 1}" in output, output)
            assertTrue('\u001B' !in output, output)
        }
        compileConsumer(wrong.replace("fishNum =", "fishNum by")) { code, output ->
            assertEquals(ExitCode.OK, code, output)
        }
    }

    @Test
    fun `explicit wrapper types receive guidance instead of a misleading single-token edit`() {
        compileConsumer(
            "class Fish : Node<Fish>() { val hunger: io.canopy.engine.core.flows.events.Signal<Int> = signal(1) }"
        ) {
                code,
                output,
            ->
            assertEquals(ExitCode.COMPILATION_ERROR, code, output)
            assertTrue("remove the explicit wrapper type" in output, output)
            assertTrue("2 +" !in output, output)
        }
        compileConsumer("class Fish : Node<Fish>() { val hunger by signal(1) }") { code, output ->
            assertEquals(ExitCode.OK, code, output)
        }
    }

    @Test
    fun `CRLF and tabbed source keep the assignment location and suggested edit aligned`() {
        compileConsumer("class Aquarium : Node<Aquarium>() {\r\n\tval fishNum = computed { 1 }\r\n}") { code, output ->
            assertEquals(ExitCode.COMPILATION_ERROR, code, output)
            assertTrue("Game.kt:3:14" in output, output)
            assertTrue("3 +     val fishNum by computed { 1 }" in output, output)
        }
    }

    @Test
    fun `one missing runtime build produces one compatibility error without a field error cascade`() {
        compileConsumer(
            "class Enemy : Node<Enemy>() { var first = 1; var second = 2; var third = 3 }",
            apiSource = "package io.canopy.engine.core.nodes; abstract class Node<N : Node<N>>"
        ) { code, output ->
            assertEquals(ExitCode.COMPILATION_ERROR, code, output)
            assertEquals(1, Regex("\\[CANOPY_NODE_PROPERTY_ABI]").findAll(output).count(), output)
            assertTrue("Engine and compiler builds are incompatible" in output, output)
            assertTrue("Publish the engine and compiler from the same checkout" in output, output)
            assertTrue("CANOPY_UNMANAGED_NODE_STATE" !in output, output)
        }
    }

    @Test
    fun `consumer typed dependencies require delegation instead of storing descriptors`() {
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
        ) { code, output ->
            assertEquals(ExitCode.COMPILATION_ERROR, code, output)
            assertTrue("CANOPY_PROPERTY_DELEGATION" in output, output)
        }
    }

    @Test
    fun `global delegates compile in all supported scopes and reject accidental descriptor storage`() {
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
        ) { code, output ->
            assertEquals(ExitCode.COMPILATION_ERROR, code, output)
            assertTrue("Use `by` for a manager reference property" in output, output)
        }
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
    fun `consumer assets require delegation and reject non asset types`() {
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
        ) { code, output ->
            assertEquals(ExitCode.COMPILATION_ERROR, code, output)
            assertTrue("CANOPY_PROPERTY_DELEGATION" in output, output)
        }
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

    @Test
    fun `constructor calls protect arguments nested local generic and anonymous nodes`() {
        compileConsumer(
            """
            val events = mutableListOf<String>()
            open class Base<N : Base<N>>(value: Int) : Node<N>() {
                init { check(constructionDepth > 0); events += "base${'$'}value" }
            }
            class Leaf(value: Int = 3) : Base<Leaf>(value) {
                init { events += "leaf" }
            }
            class Generic<T>(value: T) : Node<Generic<T>>() {
                init { check(constructionDepth > 0) }
            }
            fun argument(): Int { check(constructionDepth > 0); events += "argument"; Leaf(2); return 1 }
            fun verify(): String {
                Leaf(argument())
                Leaf()
                Generic("value")
                class Local : Node<Local>() { init { check(constructionDepth > 0) } }
                Local()
                val anonymous = object : Node<Nothing>() { init { check(constructionDepth > 0) } }
                check(constructionDepth == 0)
                check(boundaries == 6) { "boundaries=${'$'}boundaries" }
                return events.joinToString(",")
            }
            """.trimIndent(),
            execute = { loader ->
                assertEquals(
                    "argument,base2,leaf,base1,leaf,base3,leaf",
                    loader.loadClass("GameKt").getMethod("verify").invoke(null)
                )
            }
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
    }

    @Test
    fun `constructor failures leave boundary and preserve original throwable`() {
        compileConsumer(
            """
            val failure = IllegalStateException("initialization")
            class Broken : Node<Broken>() { init { check(constructionDepth > 0); throw failure } }
            inline fun make(factory: () -> Broken) = factory()
            fun verify(): Boolean {
                try { make { Broken() } } catch (caught: Throwable) {
                    return caught === failure && constructionDepth == 0 && boundaries == 1
                }
                return false
            }
            """.trimIndent(),
            execute = { loader -> assertEquals(true, loader.loadClass("GameKt").getMethod("verify").invoke(null)) }
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
    }

    @Test
    fun `constructor reference suggestions replace the entire reference and compile`() {
        // Arrange
        listOf("Leaf", "Aquarium".repeat(11)).forEach { name ->
            val wrong = "class $name : Node<$name>()\nval factory = ::$name"
            val corrected = wrong.replace("::$name", "{ $name() }")

            // Act / Assert
            compileConsumer(wrong) { code, output ->
                assertEquals(ExitCode.COMPILATION_ERROR, code, output)
                assertTrue("CANOPY_NODE_CONSTRUCTION_REFERENCE" in output, output)
                assertTrue("3 + val factory = { $name() }" in output, output)
                assertTrue("Game.kt:3:15" in output, output)
            }
            compileConsumer(corrected) { code, output -> assertEquals(ExitCode.OK, code, output) }
        }
    }

    @Test
    fun `inline construction preserves nonlocal returns from arguments`() {
        compileConsumer(
            """
            class Leaf(value: Int) : Node<Leaf>()
            fun early(): Int { Leaf(if (true) return 7 else 1); return 0 }
            fun verify(): Boolean = early() == 7 && constructionDepth == 0 && boundaries == 1
            """.trimIndent(),
            execute = { loader -> assertEquals(true, loader.loadClass("GameKt").getMethod("verify").invoke(null)) }
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
    }

    @Test
    fun `suspend argument evaluation is rejected while stored suspend callbacks compile`() {
        listOf(
            "suspend fun make() = Leaf(fetch())",
            "suspend fun make() = Leaf(run { fetch() })",
            "suspend fun make() = nodeConstruction { fetch(); Leaf(1) }",
            "suspend fun make() = nodeConstruction { val value = fetch(); Leaf(value) }"
        ).forEach { declaration ->
            compileConsumer(
                """
                class Leaf(value: Int) : Node<Leaf>()
                suspend fun fetch(): Int = 1
                $declaration
                """.trimIndent()
            ) { code, output ->
                assertEquals(ExitCode.COMPILATION_ERROR, code, output)
                assertTrue("CANOPY_NODE_CONSTRUCTION_SUSPEND" in output, output)
                assertTrue(Regex("Game\\.kt:[0-9]+:[0-9]+").containsMatchIn(output), output)
            }
        }
        compileConsumer(
            """
            class Leaf(callback: suspend () -> Int) : Node<Leaf>()
            suspend fun fetch(): Int = 1
            fun make() = Leaf { fetch() }
            inline fun stash(noinline callback: suspend () -> Int): Int = 1
            class NumberLeaf(value: Int) : Node<NumberLeaf>()
            fun stored() = NumberLeaf(stash { fetch() })
            """.trimIndent()
        ) { code, output -> assertEquals(ExitCode.OK, code, output) }
    }

    @Test
    fun `anonymous object super arguments cannot suspend during construction`() {
        compileConsumer(
            """
            open class Base<N : Base<N>>(value: Int) : Node<N>()
            suspend fun fetch(): Int = 1
            suspend fun make() = object : Base<Nothing>(fetch()) {}
            """.trimIndent()
        ) { code, output ->
            assertEquals(ExitCode.COMPILATION_ERROR, code, output)
            assertTrue("suspension functions can only be called within coroutine body" in output.lowercase(), output)
        }
    }

    @Test
    fun `construction ABI diagnostics require matching inline helper only at node calls`() {
        compileConsumer(
            "class Leaf : Node<Leaf>(); fun make() = Leaf()",
            apiSource = api.replace("inline fun <T> nodeConstruction", "fun <T> nodeConstruction")
        ) {
                code,
                output,
            ->
            assertEquals(ExitCode.COMPILATION_ERROR, code, output)
            assertTrue("CANOPY_NODE_CONSTRUCTION_ABI" in output, output)
        }
        compileConsumer(
            "class Leaf : Node<Leaf>()",
            apiSource = api.replace("inline fun <T> nodeConstruction", "fun <T> nodeConstruction")
        ) {
                code,
                output,
            ->
            assertEquals(ExitCode.OK, code, output)
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

    private fun compileApi(directory: java.io.File, apiSource: String, appApiSource: String): java.io.File {
        val apiFile = directory.resolve("Api.kt").apply { writeText(apiSource) }
        val appFile = directory.resolve("App.kt").apply { writeText(appApiSource) }
        val apiOutput = directory.resolve("api")
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
        val reactiveFile = directory.resolve("Reactive.kt").apply {
            writeText(
                """
                package io.canopy.engine.core.flows.events
                import kotlin.reflect.KProperty
                import io.canopy.engine.core.nodes.Node
                class Signal<T>(var value: T) {
                    operator fun provideDelegate(owner: Node<*>, property: KProperty<*>) = SignalProperty(this)
                    operator fun invoke(): T = value
                }
                class SignalProperty<T>(private val source: Signal<T>) {
                    operator fun getValue(owner: Node<*>, property: KProperty<*>): T = source.value
                    operator fun setValue(owner: Node<*>, property: KProperty<*>, value: T) { source.value = value }
                }
                class Computed<T>(val block: () -> T) {
                    operator fun provideDelegate(owner: Node<*>, property: KProperty<*>) = ComputedProperty(this)
                }
                class ComputedProperty<T>(private val source: Computed<T>) {
                    operator fun getValue(owner: Node<*>, property: KProperty<*>): T = source.block()
                }
                fun <T> signal(initial: T) = Signal(initial)
                """.trimIndent()
            )
        }
        val apiCode = compiler.exec(
            PrintStream(bytes),
            *common,
            "-d",
            apiOutput.path,
            apiFile.path,
            appFile.path,
            queryFile.path,
            assetFile.path,
            reactiveFile.path
        )
        assertEquals(ExitCode.OK, apiCode, bytes.toString())
        return apiOutput
    }

    private fun compileConsumer(
        source: String,
        additionalPlugin: String? = null,
        apiSource: String = api,
        appApiSource: String = appApi,
        withPlugin: Boolean = true,
        inspectOutput: ((java.io.File, java.io.File) -> Unit)? = null,
        execute: ((ClassLoader) -> Unit)? = null,
        assertResult: (ExitCode, String) -> Unit,
    ) {
        val directory = Files.createTempDirectory("canopy-consumer-compiler").toFile()
        try {
            val apiOutput = if (apiSource == api && appApiSource == appApi) {
                defaultApiOutput ?: compileApi(fixtureDirectory, apiSource, appApiSource).also { defaultApiOutput = it }
            } else {
                compileApi(directory, apiSource, appApiSource)
            }
            val bytes = ByteArrayOutputStream()
            val compiler = K2JVMCompiler()
            val game = directory.resolve("Game.kt").apply {
                writeText("import io.canopy.engine.core.nodes.*\n" + source)
            }
            val arguments = arrayOf(
                "-Xverify-ir=error",
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
                compiler.exec(
                    PrintStream(bytes),
                    *arguments.filter {
                        withPlugin || !it.startsWith("-Xplugin=")
                    }.toTypedArray()
                )
            } else {
                // Isolate plugin loading from the test worker, which already loads our main classes.
                val executable = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
                val java = java.io.File(System.getProperty("java.home"), "bin/$executable").path
                val output = directory.resolve("compiler-output.txt")
                val process = ProcessBuilder(
                    listOf(
                        java,
                        "-cp",
                        System.getProperty("canopy.compiler.runtime"),
                        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler"
                    ) + arguments
                ).redirectErrorStream(true).redirectOutput(output).start()
                try {
                    assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Compiler provider process timed out")
                    bytes.write(output.readBytes())
                    ExitCode.entries.first { it.code == process.exitValue() }
                } finally {
                    if (process.isAlive) {
                        process.destroyForcibly()
                        assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Compiler process did not stop")
                    }
                }
            }
            assertResult(code, bytes.toString())
            if (code == ExitCode.OK) inspectOutput?.invoke(directory.resolve("game"), apiOutput)
            if (code == ExitCode.OK && execute != null) {
                java.net.URLClassLoader(
                    arrayOf(apiOutput.toURI().toURL(), directory.resolve("game").toURI().toURL()),
                    javaClass.classLoader
                ).use(execute)
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
