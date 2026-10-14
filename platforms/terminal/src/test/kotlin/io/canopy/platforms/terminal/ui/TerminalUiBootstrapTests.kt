package io.canopy.platforms.terminal.ui

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.engine.app.Screen
import io.canopy.engine.app.screens
import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.logging.LoggingPolicy
import io.canopy.engine.ui.UiBackend
import io.canopy.engine.ui.UiManager
import io.canopy.platforms.terminal.app.TerminalApp

class TerminalUiBootstrapTests {
    private class StartupScreen(private val observe: () -> Unit) : Screen() {
        override fun onEnter() = observe()
    }

    @Test
    fun `screen and app entry see the same initialized terminal backend`() {
        // Arrange
        val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)
        val app = TerminalApp(terminal, {}, { Size(80, 24) })
        app.logging(LoggingPolicy.Host)
        var screenBackend: UiBackend? = null
        var appBackend: UiBackend? = null
        app.screens { start(StartupScreen { screenBackend = manager<UiManager>().backend }) }
        app.onEnter { appBackend = manager<UiManager>().backend }
        try {
            // Act
            app.enter()

            // Assert
            assertNotNull(screenBackend)
            assertSame(screenBackend, appBackend)
            assertSame(appBackend, manager<UiManager>().backend)
            manager<SceneManager>().currScene = CommandPrompt("Console")
            assertSame(appBackend, manager<UiManager>().backend)
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
        app.onEnter { manager<UiManager>().backend = custom }
        try {
            // Act
            app.enter()
            manager<SceneManager>().currScene = CommandPrompt("Console")

            // Assert
            assertSame(custom, manager<UiManager>().backend)
        } finally {
            app.exit()
        }
    }
}
