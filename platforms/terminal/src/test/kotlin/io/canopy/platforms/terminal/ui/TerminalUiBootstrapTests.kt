package io.canopy.platforms.terminal.ui

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.engine.app.initialScreen
import io.canopy.engine.app.navigateTo
import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.logging.LoggingPolicy
import io.canopy.engine.ui.UiBackend
import io.canopy.engine.ui.UiManager
import io.canopy.engine.ui.UiRoot
import io.canopy.engine.ui.components.Button
import io.canopy.platforms.terminal.app.TerminalApp

class TerminalUiBootstrapTests {
    private class StartupScreen(private val observe: () -> Unit) : Node<StartupScreen>("Startup") {
        override fun onEnterTree() = observe()
    }

    class Counter : Node<Counter>("Counter") {
        var count by signal(0)
        var button: Button<*>? = null
        override fun nodeInit() {
            UiRoot {
                Column {
                    Text("Fish: $count")
                    button = Button("Add") { count++ }
                }
            }
        }
    }
    class Other : Node<Other>("Other") {
        override fun nodeInit() {
            UiRoot { Text("Other screen") }
        }
    }

    @Test
    fun `returning to a cached screen restores live text bindings and button actions`() {
        val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)
        val frames = mutableListOf<List<TerminalUiSpan>>()
        val app = TerminalApp(terminal, {}, { Size(80, 24) })
        app.logging(LoggingPolicy.Host)
        app.initialScreen<Counter>()
        app.onEnter { getManager<UiManager>().backend = TerminalUiBackend(terminal) { frames += it } }
        try {
            app.enter()
            app.update(0f)
            val counter = getManager<io.canopy.engine.app.ScreenManager>().current as Counter
            counter.button!!.activate()
            app.update(0f)
            assertTrue(frames.last().joinToString { it.text }.contains("Fish: 1"))
            app.navigateTo<Other>()
            app.update(0f)
            counter.count = 9
            app.update(0f)
            assertTrue(frames.last().joinToString { it.text }.contains("Other screen"))
            app.navigateTo<Counter>()
            app.update(0f)
            assertSame(counter, getManager<io.canopy.engine.app.ScreenManager>().current)
            assertTrue(frames.last().joinToString { it.text }.contains("Fish: 9"))
            counter.button!!.activate()
            app.update(0f)
            assertTrue(frames.last().joinToString { it.text }.contains("Fish: 10"))
        } finally {
            app.exit()
        }
    }

    @Test
    fun `screen and app entry see the same initialized terminal backend`() {
        // Arrange
        val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)
        val app = TerminalApp(terminal, {}, { Size(80, 24) })
        app.logging(LoggingPolicy.Host)
        var screenBackend: UiBackend? = null
        var appBackend: UiBackend? = null
        app.initialScreen<StartupScreen> { StartupScreen { screenBackend = getManager<UiManager>().backend } }
        app.onEnter { appBackend = getManager<UiManager>().backend }
        try {
            // Act
            app.enter()

            // Assert
            assertNotNull(screenBackend)
            assertSame(screenBackend, appBackend)
            assertSame(appBackend, getManager<UiManager>().backend)
            getManager<SceneManager>().currScene!!.addChild(CommandPrompt("Console"))
            assertSame(appBackend, getManager<UiManager>().backend)
        } finally {
            app.exit()
        }
    }

    @Test
    fun `explicit startup backend survives app entry and command prompt mounting`() {
        // Arrange
        val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)
        val custom = TerminalUiBackend(terminal) {}
        val app = TerminalApp(terminal, {}, { Size(80, 24) })
        app.logging(LoggingPolicy.Host)
        app.onEnter { getManager<UiManager>().backend = custom }
        try {
            // Act
            app.enter()
            getManager<SceneManager>().currScene = CommandPrompt("Console")

            // Assert
            assertSame(custom, getManager<UiManager>().backend)
        } finally {
            app.exit()
        }
    }
}
