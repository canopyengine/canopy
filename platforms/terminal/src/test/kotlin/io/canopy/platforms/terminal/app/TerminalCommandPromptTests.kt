package io.canopy.platforms.terminal.app

import kotlin.test.*
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.adapters.mordant.input.MordantInputManager
import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.commands.CommandPromptHost
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.input.InputFocus
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.TextInputEvent
import io.canopy.engine.ui.UiManager
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
        val presentation = presentation({ false }, {})
        val host = CommandPromptHost(terminalApp(), presentation)
        ManagersRegistry.withScope {
            register(InputFocus())
            register(input)
            register(host)
            register(scenes)
            register(UiManager())
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
    fun `digit and punctuation toggles suppress only their paired text and modifiers preserve later input`() {
        val input = MordantInputManager()
        val scenes = SceneManager()
        val host = CommandPromptHost(terminalApp(), presentation({ false }, {}))
        ManagersRegistry.withScope {
            register(InputFocus())
            register(input)
            register(host)
            register(scenes)
            register(UiManager())
        }
        val prompt = CommandPrompt("Console") { toggleKey = Key.NUM_1 }
        scenes.currScene = prompt
        input.enqueueMordantKeyEvent(KeyboardEvent("1"))
        input.enqueueMordantKeyEvent(KeyboardEvent("x"))
        input.processEvents()
        assertTrue(prompt.isOpen)
        assertEquals("x", prompt.draft)
        prompt.toggleKey = Key.SEMICOLON
        input.enqueueMordantKeyEvent(KeyboardEvent(";"))
        input.processEvents()
        assertFalse(prompt.isOpen)
        input.enqueueMordantKeyEvent(KeyboardEvent(";"))
        input.enqueueMordantKeyEvent(KeyboardEvent("y"))
        input.processEvents()
        assertTrue(prompt.isOpen)
        assertEquals("xy", prompt.draft)
        prompt.close()
        prompt.toggleKey = Key.Z
        input.enqueueMordantKeyEvent(KeyboardEvent("Z", shift = true))
        input.enqueueMordantKeyEvent(KeyboardEvent("a"))
        input.processEvents()
        assertTrue(prompt.isOpen)
        assertEquals("xya", prompt.draft)
        prompt.close()
        input.enqueueMordantKeyEvent(KeyboardEvent("z", ctrl = true))
        // A modifier toggle emits no paired text; an independent following text event must survive.
        input.enqueue(TextInputEvent("z"))
        input.enqueueMordantKeyEvent(KeyboardEvent("x"))
        input.processEvents()
        assertTrue(prompt.isOpen)
        assertEquals("xyazx", prompt.draft)
        prompt.close()
        prompt.toggleKey = Key.NUM_LOCK
        input.enqueueMordantKeyEvent(KeyboardEvent("NumLock"))
        input.enqueue(TextInputEvent("LOCK"))
        input.processEvents()
        assertTrue(prompt.isOpen)
        assertEquals("xyazxLOCK", prompt.draft)
    }

    @Test
    fun `raw presentation changes only owned rows on redraw and hide while filtering control sequences`() {
        val output = mutableListOf<String>()
        val presentation = presentation({ false }, output::add)
        val host = CommandPromptHost(terminalApp(), presentation)
        val scenes = SceneManager()
        ManagersRegistry.withScope {
            register(InputFocus())
            register(host)
            register(scenes)
            register(UiManager())
        }
        val prompt = CommandPrompt("Console") {
            command("say") { execute { reply("first\nsecond\u001b[31m") } }
        }
        scenes.currScene = prompt
        prompt.open()
        prompt.submit("say")
        host.onUpdate(0f)
        assertFalse(output.last().contains("\u001b[2J"))
        assertTrue(output.last().contains("first"))
        assertTrue(output.last().contains("second"))
        val count = output.size
        host.onUpdate(0f)
        assertEquals(count, output.size)
        prompt.close()
        host.onUpdate(0f)
        assertEquals("world", terminalTestRows(Terminal(ansiLevel = AnsiLevel.TRUECOLOR), output)[0])
        assertFalse(output.last().contains("world"))
        assertFalse(output.last().contains("first"))
        assertFalse(output.last().contains("second"))
        assertFalse(presentation.isVisible)
    }

    @Test
    fun `line output cursor emits identical bounded replies exactly once and trimming emits nothing`() {
        val output = mutableListOf<String>()
        val presentation = presentation({ true }, output::add)
        val host = CommandPromptHost(terminalApp(), presentation)
        val scenes = SceneManager()
        ManagersRegistry.withScope {
            register(InputFocus())
            register(host)
            register(scenes)
            register(UiManager())
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
        assertTrue(presentation.isVisible)
    }

    @Test
    fun `switching from raw to line mode redraws retained transcript without cursor movement`() {
        var line = false
        val output = mutableListOf<String>()
        val presentation = presentation({ line }, output::add)
        val host = CommandPromptHost(terminalApp(), presentation)
        val scenes = SceneManager()
        ManagersRegistry.withScope {
            register(InputFocus())
            register(host)
            register(scenes)
            register(UiManager())
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
    private fun presentation(line: () -> Boolean, output: (String) -> Unit): TerminalCommandPresentation {
        val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)
        val surface = TerminalSurface(terminal, { Size(40, 5) }, output, line)
        surface.renderWorld(listOf("world"))
        return TerminalCommandPresentation(
            line,
            output,
            TerminalCommandUi(terminal, { Size(40, 5) }, surface, { 3 }, { 1.0 })
        )
    }
}
