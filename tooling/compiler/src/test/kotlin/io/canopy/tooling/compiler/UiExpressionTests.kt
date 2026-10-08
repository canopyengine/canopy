package io.canopy.tooling.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler

/** Executes generated closures, rather than merely checking whether transformed source compiles. */
class UiExpressionTests {
    @Test
    fun `direct interpolation captures current values without rerunning initializers or handlers`() {
        compileAndRun(
            """
            import io.canopy.engine.ui.*
            fun verify(): String {
                var count = 1
                var initialized = 0
                var clicked = 0
                val scope = UiScope()
                with(scope) {
                    val state = ++initialized
                    Text("Animals: ${'$'}count / ${'$'}state")
                    Button("Add ${'$'}count") { clicked += count }
                }
                count = 7
                scope.flush()
                scope.click()
                return "${'$'}{scope.values}|${'$'}initialized|${'$'}clicked"
            }
            """.trimIndent(),
            "[Animals: 7 / 1, Add 7]|1|7"
        )
    }

    @Test
    fun `conditionals and keyed loops reevaluate in their own receiver scopes`() {
        compileAndRun(
            """
            import io.canopy.engine.ui.*
            fun verify(): String {
                var show = true
                var items = listOf("a", "b")
                var initialized = 0
                val scope = UiScope()
                with(scope) {
                    val state = ++initialized
                    if (show) { Text("Shown ${'$'}state") } else { Text("Hidden ${'$'}state") }
                    for (item in items) { key(item) { Text(item) } }
                }
                show = false
                items = listOf("b", "c", "a")
                scope.flush()
                return "${'$'}{scope.values}|${'$'}initialized|${'$'}{scope.regions.size}"
            }
            """.trimIndent(),
            "[Hidden 1, b, c, a]|1|2"
        )
    }

    @Test
    fun `reusable function and class calls have distinct structural sites and live arguments`() {
        compileAndRun(
            """
            import io.canopy.engine.ui.*
            fun UiScope.Card(label: String) { Text(label) }
            class CardComponent { fun render(scope: UiScope, label: String) { with(scope) { Text(label) } } }
            fun verify(): String {
                var count = 1
                val scope = UiScope()
                val card = CardComponent()
                with(scope) {
                    Card("left ${'$'}count")
                    Card("right ${'$'}count")
                    card.render(this, "class ${'$'}count")
                }
                count = 7
                scope.flush()
                return "${'$'}{scope.values}|${'$'}{scope.regions.size}"
            }
            """.trimIndent(),
            "[left 7, right 7, class 7]|3"
        )
    }

    @Test
    fun `layout style visibility and action eligibility setters capture property expressions`() {
        compileAndRun(
            """
            import io.canopy.engine.ui.*
            fun verify(): String {
                var width = 2
                var shown = true
                val scope = UiScope()
                lateinit var element: UiElement
                with(scope) {
                    element = Text("hello")
                    element.style = UiStyle(width)
                    element.isVisible = shown
                    element.enabled = shown
                }
                width = 9
                shown = false
                scope.flush()
                return "${'$'}{element.style.width}|${'$'}{element.isVisible}|${'$'}{element.enabled}"
            }
            """.trimIndent(),
            "9|false|false"
        )
    }

    @Test
    fun `derived frozen strings and value producing structural conditionals have source diagnostics`() {
        compileAndRun(
            """
            import io.canopy.engine.ui.*
            fun verify(): String {
                var count = 2
                with(UiScope()) { val label = "Count ${'$'}count"; Text(label) }
                return "unused"
            }
            """.trimIndent(),
            "CANOPY_UI_UNSUPPORTED",
            expectError = true
        )
        compileAndRun(
            """
            import io.canopy.engine.ui.*
            fun verify(): String {
                with(UiScope()) { val label = if (true) Text("a") else Text("b") }
                return "unused"
            }
            """.trimIndent(),
            "CANOPY_UI_UNSUPPORTED",
            expectError = true
        )
    }

    @Test
    fun `structural locals are recomputed and component constructors are remembered`() {
        compileAndRun(
            """
            import io.canopy.engine.ui.*
            var initialized = 0
            class CardComponent {
                init { initialized++ }
                fun render(scope: UiScope, label: String) { with(scope) { Text(label) } }
            }
            fun verify(): String {
                var count = 1
                val scope = UiScope()
                with(scope) {
                    if (count > 0) {
                        val card = CardComponent()
                        val label = "Count ${'$'}count"
                        card.render(this, label)
                    }
                }
                count = 7
                scope.flush()
                return "${'$'}{scope.values}|${'$'}initialized"
            }
            """.trimIndent(),
            "[Count 7]|1"
        )
    }

    @Test
    fun `a keyed sibling does not authorize unkeyed repeated children`() {
        compileAndRun(
            """
            import io.canopy.engine.ui.*
            fun verify(): String {
                with(UiScope()) {
                    for (item in listOf("a", "b")) {
                        key(item) { Text(item) }
                        Text("unkeyed")
                    }
                }
                return "unused"
            }
            """.trimIndent(),
            "Each declarative for-loop item",
            expectError = true
        )
    }

    @Test
    fun `ordinary zero argument calls inside and outside declarations remain untouched`() {
        compileAndRun(
            """
            import io.canopy.engine.ui.*
            fun ordinary(): String = "ordinary"
            fun verify(): String {
                val outside = ordinary()
                val scope = UiScope()
                with(scope) { Text(ordinary()); ordinary() }
                scope.flush()
                return "${'$'}outside|${'$'}{scope.values}"
            }
            """.trimIndent(),
            "ordinary|[ordinary]"
        )
    }

    private fun compileAndRun(source: String, expected: String, expectError: Boolean = false) {
        val directory = Files.createTempDirectory("canopy-ui-compiler").toFile()
        try {
            val api = directory.resolve("Ui.kt").apply {
                writeText(
                    """
                    package io.canopy.engine.ui
                    data class UiStyle(val width: Int)
                    open class BaseNode { var isVisible = true }
                    class UiElement : BaseNode() { var style = UiStyle(0); var enabled = true }
                    class UiScope {
                        val remembered = mutableMapOf<String, Any?>()
                        @Suppress("UNCHECKED_CAST")
                        fun <T> remember(site: String, value: () -> T): T = remembered.getOrPut(site) { value() } as T
                        val properties = mutableListOf<() -> Unit>()
                        val regions = mutableListOf<UiScope.() -> Unit>()
                        var evaluatingRegion = false
                        val evaluators = mutableListOf<() -> String>()
                        val values = mutableListOf<String>()
                        var handler: () -> Unit = {}
                        fun Text(value: String): UiElement { values += value; return UiElement() }
                        fun Button(label: String, onClick: () -> Unit): UiElement {
                            handler = onClick; values += label; return UiElement()
                        }
                        fun bindText(site: String, value: () -> String): UiElement {
                            evaluators += value; values += value(); return UiElement()
                        }
                        fun bindButtonText(site: String, label: () -> String, onClick: () -> Unit): UiElement {
                            handler = onClick; return bindText(site, label)
                        }
                        fun structure(site: String, content: UiScope.() -> Unit) {
                            // Nested regions belong to their parent. Replaying them as independent roots
                            // would retain the parent's previous captured locals after reconstruction.
                            if (!evaluatingRegion) regions += content
                            val previous = evaluatingRegion
                            evaluatingRegion = true
                            try { content() } finally { evaluatingRegion = previous }
                        }
                        fun key(value: Any, content: UiScope.() -> Unit) = content()
                        fun bindStyle(site: String, element: UiElement, value: () -> UiStyle) {
                            properties += { element.style = value() }; element.style = value()
                        }
                        fun bindVisible(site: String, element: UiElement, value: () -> Boolean) {
                            properties += { element.isVisible = value() }; element.isVisible = value()
                        }
                        fun bindEnabled(site: String, element: UiElement, value: () -> Boolean) {
                            properties += { element.enabled = value() }; element.enabled = value()
                        }
                        fun flush() {
                            properties.forEach { it() }
                            values.clear()
                            if (regions.isEmpty()) evaluators.forEach { values += it() }
                            else {
                                evaluators.clear()
                                evaluatingRegion = true
                                try { regions.toList().forEach { it() } }
                                finally { evaluatingRegion = false }
                            }
                        }
                        fun click() = handler()
                    }
                    """.trimIndent()
                )
            }
            val game = directory.resolve("Game.kt").apply { writeText(source) }
            val standardLibrary = File(kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI()).path
            val output = directory.resolve("classes")
            val messages = ByteArrayOutputStream()
            val compiler = K2JVMCompiler()
            val common = arrayOf("-no-stdlib", "-no-reflect", "-classpath", standardLibrary, "-d", output.path)
            assertEquals(ExitCode.OK, compiler.exec(PrintStream(messages), *common, api.path), messages.toString())
            messages.reset()
            val code = compiler.exec(
                PrintStream(messages), "-no-stdlib", "-no-reflect", "-classpath",
                standardLibrary + File.pathSeparator + output.path,
                "-Xplugin=" + System.getProperty("canopy.compiler.jar"), "-d", output.path, game.path
            )
            if (expectError) {
                assertEquals(ExitCode.COMPILATION_ERROR, code, messages.toString())
                kotlin.test.assertTrue(expected in messages.toString(), messages.toString())
                return
            }
            assertEquals(ExitCode.OK, code, messages.toString())
            URLClassLoader(arrayOf(output.toURI().toURL()), javaClass.classLoader).use { loader ->
                assertEquals(expected, loader.loadClass("GameKt").getMethod("verify").invoke(null))
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
