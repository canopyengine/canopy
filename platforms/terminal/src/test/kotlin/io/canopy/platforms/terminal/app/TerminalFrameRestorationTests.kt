package io.canopy.platforms.terminal.app

import kotlin.test.*
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.commands.CommandPromptHost
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import org.junit.jupiter.api.AfterEach

class TerminalFrameRestorationTests {
    private val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)
    private val output = mutableListOf<String>()
    private var app: TerminalApp? = null

    @AfterEach
    fun cleanup() {
        app?.engineLoop?.exit()
        ManagersRegistry.exit()
    }

    @Test
    fun `closing prompt restores a copied latest suppressed frame without another world update`() {
        // Arrange: use the real app-installed presentation and host, with no input reader or TTY.
        val (app, host, prompt) = start()
        app.renderFrame(listOf("old frame", "old row"))
        prompt.show()
        host.onUpdate(0f)
        output.clear()

        // Act: updates continue behind the prompt, and callers may reuse their list afterwards.
        app.renderFrame(listOf("intermediate"))
        val latest = mutableListOf("latest")
        app.renderFrame(latest)
        latest[0] = "caller mutation"
        host.onUpdate(0f)
        assertTrue(output.isEmpty())
        prompt.hide()
        host.onUpdate(0f)

        // Assert: the short replacement clears old/prompt rows and uses copied text, without renderFrame again.
        assertEquals(listOf("\u001b[2J\u001b[H", buildTerminalFrame(terminal, listOf("latest"))), output)
        host.onUpdate(0f)
        assertEquals(2, output.size)
    }

    @Test
    fun `closing prompt restores the frame submitted before opening when world is paused`() {
        // Arrange
        val (app, host, prompt) = start()
        app.renderFrame(listOf("paused world"))
        app.pause()
        prompt.show()
        host.onUpdate(0f)
        output.clear()

        // Act
        prompt.hide()
        host.onUpdate(0f)

        // Assert
        assertEquals(buildTerminalFrame(terminal, listOf("paused world")), output.last())
        assertEquals(0L, app.frameCount)
        assertTrue(app.isPaused)
    }

    @Test
    fun `tree exit restores a submitted empty frame instead of an older world`() {
        // Arrange
        val (app, host, prompt) = start()
        app.renderFrame(listOf("old world"))
        prompt.show()
        host.onUpdate(0f)
        app.renderFrame(emptyList())
        output.clear()

        // Act
        ManagersRegistry.getManager(SceneManager::class).currScene = null

        // Assert
        assertEquals(listOf("\u001b[2J\u001b[H", buildTerminalFrame(terminal, emptyList())), output)
    }

    @Test
    fun `closing prompt without a submitted frame only clears presentation`() {
        // Arrange
        val (_, host, prompt) = start()
        prompt.show()
        host.onUpdate(0f)
        output.clear()

        // Act
        prompt.hide()
        host.onUpdate(0f)

        // Assert
        assertEquals(listOf("\u001b[2J\u001b[H"), output)
    }

    @Test
    fun `failed restoration output releases presentation ownership for later world rendering`() {
        // Arrange
        var writesUntilFailure: Int? = null
        val (app, host, prompt) = start { text ->
            writesUntilFailure?.let {
                check(it > 0) { "output unavailable" }
                writesUntilFailure = it - 1
            }
            output.add(text)
        }
        prompt.show()
        host.onUpdate(0f)
        app.renderFrame(listOf("retained"))
        output.clear()
        writesUntilFailure = 1

        // Act
        prompt.hide()
        assertFailsWith<IllegalStateException> { host.onUpdate(0f) }
        writesUntilFailure = null
        app.renderFrame(listOf("recovered"))

        // Assert
        assertEquals(listOf("\u001b[2J\u001b[H", buildTerminalFrame(terminal, listOf("recovered"))), output)
    }

    private fun start(write: (String) -> Unit = output::add): Triple<TerminalApp, CommandPromptHost, CommandPrompt> {
        val app = TerminalApp(terminal, write)
        this.app = app
        app.engineLoop.enter()
        val host = ManagersRegistry.getManager(CommandPromptHost::class)
        val prompt = CommandPrompt("Console")
        ManagersRegistry.getManager(SceneManager::class).currScene = prompt
        return Triple(app, host, prompt)
    }
}
