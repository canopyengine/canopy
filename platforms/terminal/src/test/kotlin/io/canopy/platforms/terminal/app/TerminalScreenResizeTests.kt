package io.canopy.platforms.terminal.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.engine.app.Screen
import io.canopy.engine.app.ScreenManager
import io.canopy.engine.app.screens
import io.canopy.engine.core.managers.ManagersRegistry

class TerminalScreenResizeTests {
    private open class ResponsiveScreen(private val app: TerminalApp, private val label: String) : Screen() {
        val dimensions = mutableListOf<Pair<Int, Int>>()

        override fun onResize(width: Int, height: Int) {
            dimensions.add(width to height)
            app.renderFrame(listOf("$label $width,$height"))
        }
    }

    private class First(app: TerminalApp) : ResponsiveScreen(app, "first")
    private class Second(app: TerminalApp) : ResponsiveScreen(app, "second")

    @Test
    fun `navigation lays out new screen without waiting for another console resize`() {
        var viewport = Size(30, 10)
        val output = mutableListOf<String>()
        val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)
        val app = TerminalApp(terminal, output::add, { viewport })
        val first = First(app)
        val second = Second(app)
        app.screens {
            +first
            +second
            start<First>()
        }
        try {
            app.engineLoop.enter()
            app.updateTerminalFrame(0f)
            assertEquals(listOf(30 to 10), first.dimensions)
            ManagersRegistry.getManager(ScreenManager::class).start(Second::class)
            assertEquals(listOf(30 to 10), second.dimensions)
            assertTrue(output.last().contains("second 30,10"))
            app.updateTerminalFrame(0f)
            assertEquals(listOf(30 to 10), second.dimensions)
            viewport = Size(15, 5)
            app.updateTerminalFrame(0f)
            assertEquals(listOf(30 to 10, 15 to 5), second.dimensions)
            assertEquals(listOf(30 to 10), first.dimensions)
        } finally {
            app.screens { }
            app.engineLoop.exit()
            ManagersRegistry.exit()
        }
    }
}
