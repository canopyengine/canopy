package io.canopy.platforms.terminal.app

import kotlin.test.*
import com.github.ajalt.mordant.input.KeyboardEvent
import io.canopy.adapters.mordant.input.MordantInputManager
import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.commands.CommandPromptHost
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.input.binds.Key
import org.junit.jupiter.api.AfterEach

class TerminalCommandPromptTests {
    @AfterEach
    fun cleanup() {
        ManagersRegistry.exit()
    }

    @Test
    fun `fallback acknowledgement belongs to the complete drained submission and waits for rendering`() {
        val input = MordantInputManager()
        val bridge = TerminalLineInputBridge(input)
        assertNull(bridge.processEvents())
        var lineMode = false
        val ready = bridge.preparePresentation { lineMode = true }
        assertTrue(lineMode)
        assertFalse(ready.isCompleted)
        assertSame(ready, bridge.processEvents())
        assertTrue(input.eventsThisFrame.isEmpty())
        ready.complete(Unit)
        val first = bridge.submit("one")
        assertFalse(first.isCompleted)
        assertFailsWith<IllegalStateException> { bridge.submit("too soon") }
        val drained = bridge.processEvents()
        assertSame(first, drained)
        assertEquals(2, input.eventsThisFrame.size)
        assertFalse(first.isCompleted)
        // The frame renderer acknowledges only after its output is complete.
        drained!!.complete(Unit)
        assertTrue(first.isCompleted)
        assertNull(bridge.processEvents())
        val second = bridge.submit("two")
        assertSame(second, bridge.processEvents())
        assertEquals(2, input.eventsThisFrame.size)
    }

    @Test
    fun `actual Mordant printable toggle never enters draft and modifiers suppress text`() {
        // Arrange
        val input = MordantInputManager()
        val scenes = SceneManager()
        val presentation = TerminalCommandPresentation({ false }, {}, {})
        val host = CommandPromptHost(terminalApp(), presentation)
        ManagersRegistry.withScope {
            register(input)
            register(host)
            register(scenes)
        }
        val prompt = CommandPrompt("Console") { toggleKey = Key.Q_KEY }
        scenes.currScene = prompt
        // Act
        input.enqueueMordantKeyEvent(KeyboardEvent("q"))
        input.enqueueMordantKeyEvent(KeyboardEvent("x", ctrl = true))
        input.enqueueMordantKeyEvent(KeyboardEvent("a", alt = true))
        input.enqueueMordantKeyEvent(KeyboardEvent("Z", shift = true))
        input.enqueueMordantKeyEvent(KeyboardEvent("😀"))
        input.processEvents()
        // Assert
        assertTrue(prompt.isOpen)
        assertEquals("Z😀", prompt.draft)
        assertTrue(input.eventsThisFrame.isEmpty())
        input.enqueueMordantKeyEvent(KeyboardEvent("Backspace"))
        input.processEvents()
        assertEquals("Z", prompt.draft)
        prompt.toggleKey = Key.SPACE
        input.enqueueMordantKeyEvent(KeyboardEvent(" "))
        input.processEvents()
        assertFalse(prompt.isOpen)
        input.enqueueMordantKeyEvent(KeyboardEvent(" "))
        input.processEvents()
        assertTrue(prompt.isOpen)
        assertEquals("Z", prompt.draft)
        prompt.toggleKey = Key.W
        input.enqueueMordantKeyEvent(KeyboardEvent("w"))
        input.processEvents()
        assertFalse(prompt.isOpen)
    }

    @Test
    fun `raw presentation clears rows on redraw and hide while filtering control sequences`() {
        val output = mutableListOf<String>()
        var restored = 0
        val presentation = TerminalCommandPresentation({ false }, output::add, { restored++ })
        val host = CommandPromptHost(terminalApp(), presentation)
        val scenes = SceneManager()
        ManagersRegistry.withScope {
            register(host)
            register(scenes)
        }
        val prompt = CommandPrompt("Console") {
            command("say") { execute { reply("first\nsecond\u001b[31m") } }
        }
        scenes.currScene = prompt
        prompt.open()
        prompt.submit("say")
        host.onUpdate(0f)
        assertTrue(output.last().startsWith("\u001b[2J\u001b[H"))
        assertTrue(output.last().contains("first\nsecond[31m"))
        val count = output.size
        host.onUpdate(0f)
        assertEquals(count, output.size)
        prompt.close()
        host.onUpdate(0f)
        assertEquals("\u001b[2J\u001b[H", output.last())
        assertEquals(1, restored)
        assertFalse(presentation.isVisible)
    }

    @Test
    fun `line output cursor emits identical bounded replies exactly once and trimming emits nothing`() {
        val output = mutableListOf<String>()
        var restored = 0
        val presentation = TerminalCommandPresentation({ true }, output::add, { restored++ })
        val host = CommandPromptHost(terminalApp(), presentation)
        val scenes = SceneManager()
        ManagersRegistry.withScope {
            register(host)
            register(scenes)
        }
        val prompt = CommandPrompt("Console") {
            transcriptLimit = 2
            command("say") { execute { reply("same") } }
        }
        scenes.currScene = prompt
        prompt.open()
        prompt.submit("say")
        host.onUpdate(0f)
        output.clear()
        prompt.submit("say")
        host.onUpdate(0f)
        assertEquals(listOf("> say\nsame\n", "> "), output)
        output.clear()
        prompt.transcriptLimit = 1
        host.onUpdate(0f)
        assertTrue(output.isEmpty())
        prompt.close()
        host.onUpdate(0f)
        prompt.open()
        host.onUpdate(0f)
        assertEquals(listOf("\n", "same\n", "> "), output)
        assertTrue(output.none { '\u001b' in it })
        assertEquals(0, restored)
    }

    @Test
    fun `switching from raw to line mode redraws retained transcript without cursor movement`() {
        var line = false
        val output = mutableListOf<String>()
        val presentation = TerminalCommandPresentation({ line }, output::add, {})
        val host = CommandPromptHost(terminalApp(), presentation)
        val scenes = SceneManager()
        ManagersRegistry.withScope {
            register(host)
            register(scenes)
        }
        val prompt = CommandPrompt("Console") { command("say") { execute { reply("reply") } } }
        scenes.currScene = prompt
        prompt.open()
        prompt.submit("say")
        host.onUpdate(0f)
        line = true
        output.clear()
        host.onUpdate(0f)
        assertEquals(listOf("> say\nreply\n", "> "), output)
        host.onUpdate(0f)
        assertEquals(2, output.size)
    }
}
