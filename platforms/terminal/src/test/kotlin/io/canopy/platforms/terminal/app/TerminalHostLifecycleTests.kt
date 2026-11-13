package io.canopy.platforms.terminal.app

import kotlin.test.*
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.engine.app.HostState
import io.canopy.engine.app.ScreenManager
import io.canopy.engine.app.initialScreen
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.events.MouseMoveEvent
import io.canopy.engine.logging.LoggingPolicy
import io.canopy.engine.math.Vector2
import io.canopy.engine.ui.UiRoot
import io.canopy.engine.ui.components.Button
import io.canopy.platforms.terminal.graphics.terminalTestRows

class TerminalHostLifecycleTests {
    class Game : Node<Game>("Game") {
        var button: Button<*>? = null
        var fish by signal(1)
        override fun nodeInit() {
            UiRoot {
                Column {
                    Text("Fish: $fish")
                    button = Button("Feed") { fish++ }
                }
            }
        }
    }

    @Test
    fun `hidden line input acknowledges discarded input without waiting for presentation`() {
        // Arrange
        val app = TerminalApp(Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true), {}) { Size(40, 10) }
        app.logging(LoggingPolicy.Host)
        app.enter()
        try {
            app.engineLoop.hostStateChanged(HostState(visible = false))
            val prepared = app.prepareLineInputPresentation()

            // Act
            app.update(0f)

            // Assert
            assertTrue(prepared.isCompleted)
        } finally {
            app.exit()
        }
    }

    @Test
    fun `hidden terminal retains content and repaints on return while focus loss clears pointer hover`() {
        // Arrange
        val writes = mutableListOf<String>()
        val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)
        val app = TerminalApp(terminal, writes::add) {
            Size(40, 10)
        }
        app.logging(LoggingPolicy.Host)
        app.initialScreen<Game>()
        app.enter()
        try {
            app.update(0f)
            val root = assertIs<Game>(getManager<ScreenManager>().current)
            getManager<InputManager>().enqueue(MouseMoveEvent(Vector2(0f, 1f), "pointer"))
            app.update(0f)
            assertTrue(root.button!!.isHovered)

            // Act
            app.engineLoop.hostStateChanged(HostState(focused = false))
            assertFalse(root.button!!.isHovered)
            app.engineLoop.hostStateChanged(HostState(visible = false))
            writes.clear()
            root.fish = 9
            app.renderFrame(listOf("", "", "hidden world"))
            app.update(0f)
            assertTrue(writes.isEmpty())
            app.engineLoop.hostStateChanged(HostState())
            app.update(0f)

            // Assert
            assertSame(root, getManager<ScreenManager>().current)
            val rows = terminalTestRows(terminal, writes)
            assertEquals("Fish: 9", rows[0]?.trimEnd())
            assertEquals("hidden world", rows[2]?.trimEnd())
            writes.clear()
            app.engineLoop.hostStateChanged(HostState(suspended = true))
            app.update(20f)
            assertTrue(writes.isEmpty())
            app.exit()
            assertTrue(root.isFreed)
        } finally {
            app.exit()
        }
    }
}
