package io.canopy.platforms.terminal.app

import kotlin.test.*
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.engine.app.initialScreen
import io.canopy.engine.app.navigateTo
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.nodes.Node

class TerminalScreenResizeTests {
    private open class Responsive<N : Responsive<N>>(private val app: TerminalApp, private val label: String) :
        Node<N>(label) {
        val dimensions = mutableListOf<Pair<Int, Int>>()
        private fun resize(width: Int, height: Int) {
            dimensions.add(width to height)
            app.renderFrame(listOf("$label $width,$height"))
        }
        override fun onEnterTree() {
            val size = sceneManager.sceneSize()
            resize(size.x.toInt(), size.y.toInt())
            sceneManager.onResize.connect { width, height -> resize(width, height) }
        }
    }
    private class First(app: TerminalApp) : Responsive<First>(app, "first")
    private class Second(app: TerminalApp) : Responsive<Second>(app, "second")

    @Test
    fun `cached roots initialize layout from current dimensions and reconnect resize on return`() {
        var viewport = Size(30, 10)
        val output = mutableListOf<String>()
        val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)
        val app = TerminalApp(terminal, output::add, { viewport })
        lateinit var first: First
        lateinit var second: Second
        app.initialScreen<First> { First(app).also { first = it } }
        try {
            app.enter()
            first.dimensions.clear()
            app.update(0f)
            assertEquals(listOf(30 to 10), first.dimensions)
            app.navigateTo<Second> { Second(app).also { second = it } }
            app.update(0f)
            assertEquals(listOf(30 to 10), second.dimensions)
            assertTrue(output.last().contains("second 30,10"))
            viewport = Size(15, 5)
            app.update(0f)
            assertEquals(listOf(30 to 10, 15 to 5), second.dimensions)
            assertEquals(listOf(30 to 10), first.dimensions)
            app.navigateTo<First>()
            app.update(0f)
            assertEquals(listOf(30 to 10, 15 to 5), first.dimensions)
            viewport = Size(12, 4)
            app.update(0f)
            assertEquals(12 to 4, first.dimensions.last())
            assertEquals(listOf(30 to 10, 15 to 5), second.dimensions)
        } finally {
            app.exit()
            ManagersRegistry.exit()
        }
    }
}
