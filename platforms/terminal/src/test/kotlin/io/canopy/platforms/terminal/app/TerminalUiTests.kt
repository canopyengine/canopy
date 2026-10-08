package io.canopy.platforms.terminal.app

import kotlin.test.*
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.adapters.mordant.input.MordantInputManager
import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.ui.UiElement
import io.canopy.engine.ui.UiLength
import io.canopy.engine.ui.UiRect
import io.canopy.engine.ui.UiRoot
import io.canopy.engine.ui.UiSize
import io.canopy.engine.ui.UiStyle
import org.junit.jupiter.api.AfterEach

class TerminalUiTests {
    private val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)
    private var app: TerminalApp? = null

    @AfterEach
    fun cleanup() {
        app?.engineLoop?.exit()
        ManagersRegistry.exit()
    }

    @Test
    fun `common fill and fraction layout follows both console dimensions without resize handlers`() {
        var viewport = Size(41, 12)
        val output = mutableListOf<String>()
        val app = TerminalApp(terminal, output::add, { viewport }).also { this.app = it }
        app.engineLoop.enter()
        lateinit var column: UiElement
        lateinit var row: UiElement
        lateinit var first: UiElement
        lateinit var second: UiElement
        val ui = UiRoot {
            column = Column(UiStyle(width = UiLength.Fill, height = UiLength.Fill)) {
                row = Row(UiStyle(width = UiLength.Fill, height = UiLength.Fraction(0.5))) {
                    first = Text("森林 👩‍💻")
                    first.style = UiStyle(width = UiLength.Fraction(0.5), height = UiLength.Fill)
                    second = Text("second")
                    second.style = UiStyle(width = UiLength.Fill, height = UiLength.Fill)
                }
            }
        }
        ManagersRegistry.getManager(SceneManager::class).currScene = ui
        app.updateTerminalFrame(0f)
        assertEquals(UiRect(0.0, 0.0, 40.0, 12.0), column.bounds)
        assertEquals(UiRect(0.0, 0.0, 20.0, 6.0), first.bounds)
        assertEquals(20.0, second.bounds.x)
        viewport = Size(21, 6)
        app.updateTerminalFrame(0f)
        assertEquals(UiRect(0.0, 0.0, 20.0, 6.0), column.bounds)
        assertEquals(3.0, row.bounds.height)
        assertEquals(10.0, first.bounds.width)
        assertEquals(10.0, second.bounds.x)
        assertContains(output.last(), "森林 👩‍💻")
    }

    @Test
    fun `hidden and detached UI repaint underlying world and unchanged frames are suppressed`() {
        val output = mutableListOf<String>()
        val app = TerminalApp(terminal, output::add, { Size(21, 5) }).also { this.app = it }
        app.engineLoop.enter()
        app.renderFrame(listOf("world"))
        val ui = UiRoot { Text("overlay") }
        val scenes = ManagersRegistry.getManager(SceneManager::class)
        scenes.currScene = ui
        app.updateTerminalFrame(0f)
        assertContains(output.last(), "overlay")
        val previous = output.size
        app.updateTerminalFrame(0f)
        assertEquals(previous, output.size)
        ui.hide()
        app.updateTerminalFrame(0f)
        assertContains(output.last(), "world")
        assertFalse(output.last().contains("overlay"))
        ui.show()
        app.updateTerminalFrame(0f)
        assertContains(output.last(), "overlay")
        scenes.currScene = null
        app.updateTerminalFrame(0f)
        assertFalse(output.last().contains("overlay"))
    }

    @Test
    fun `prompt shared root stays above later UI roots and visibility preserves activation and draft`() {
        val output = mutableListOf<String>()
        val app = TerminalApp(terminal, output::add, { Size(21, 5) }).also { this.app = it }
        app.engineLoop.enter()
        val scene = UiTestScene()
        val prompt = CommandPrompt("Console")
        scene.addChild(prompt)
        ManagersRegistry.getManager(SceneManager::class).currScene = scene
        prompt.open()
        val late = UiRoot { Text("late0\nlate1\nlate2\nlate3\nlate4") }
        late.zIndex = Int.MAX_VALUE
        scene.addChild(late)
        val input = ManagersRegistry.getManager(MordantInputManager::class)
        input.enqueueMordantKeyEvent(KeyboardEvent("a"))
        input.processEvents()
        app.updateTerminalFrame(0f)
        assertEquals("a", prompt.draft)
        assertContains(output.last(), "> a")
        assertFalse(output.last().contains("late4"))
        prompt.hide()
        app.updateTerminalFrame(0f)
        assertTrue(prompt.isOpen)
        assertEquals("a", prompt.draft)
        assertContains(output.last(), "late4")
        prompt.show()
        app.updateTerminalFrame(0f)
        assertContains(output.last(), "> a")
        assertFalse(app.isPaused)
    }

    @Test
    fun `open prompt captures command input before arbitrarily high content focus priority`() {
        val output = mutableListOf<String>()
        val app = TerminalApp(terminal, output::add, { Size(41, 8) }).also { this.app = it }
        app.engineLoop.enter()
        var clicks = 0
        var executions = 0
        lateinit var button: UiElement
        val root = UiRoot { button = Button("game action") { clicks++ } }
        root.zIndex = Int.MAX_VALUE
        val scene = UiTestScene()
        val prompt = CommandPrompt("Console") {
            command("run") { execute { executions++ } }
        }
        scene.addChild(root)
        scene.addChild(prompt)
        ManagersRegistry.getManager(SceneManager::class).currScene = scene
        app.updateTerminalFrame(0f)
        root.focus(button)
        prompt.open()
        app.pause()
        val input = ManagersRegistry.getManager(MordantInputManager::class)
        for (key in listOf("r", "u", "n", "Enter")) {
            input.enqueueMordantKeyEvent(KeyboardEvent(key))
        }
        input.processEvents()
        app.updateTerminalFrame(0f)
        assertEquals(1, executions)
        assertEquals(0, clicks)
        assertEquals("", prompt.draft)
        assertTrue(prompt.isOpen)
        assertTrue(app.isPaused)
    }

    @Test
    fun `terminal backend clips graphemes and overlap removes whole wide cells`() {
        var spans = emptyList<TerminalUiSpan>()
        val backend = TerminalUiBackend(terminal) { spans = it }
        assertEquals(UiSize(4.0, 1.0), backend.measureText("界👩‍💻", 8.0, true))
        assertEquals(UiSize(2.0, 2.0), backend.measureText("界👩‍💻", 2.0, true))
        backend.begin(UiSize(5.0, 2.0))
        val clip = UiRect(0.0, 0.0, 5.0, 2.0)
        backend.drawText("界👩‍💻", clip, clip, false, false)
        backend.drawText("x", UiRect(1.0, 0.0, 1.0, 1.0), clip, true, false)
        backend.drawText("界", UiRect(4.0, 1.0, 2.0, 1.0), clip, false, false)
        backend.end()
        assertEquals(listOf(TerminalUiSpan(1, 0, "\u001b[7mx\u001b[0m👩‍💻")), spans)
        backend.begin(UiSize(5.0, 2.0))
        backend.end()
        assertTrue(spans.isEmpty())
    }
}

private class UiTestScene : Node<UiTestScene>("Scene")
