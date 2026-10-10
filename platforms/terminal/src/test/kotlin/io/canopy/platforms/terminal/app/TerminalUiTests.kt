package io.canopy.platforms.terminal.app

import kotlin.test.*
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.rendering.TextStyles
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
    fun `resize and update callbacks share one physical frame write`() {
        val output = mutableListOf<String>()
        val app = TerminalApp(terminal, output::add, { Size(21, 5) }).also { this.app = it }
        app.engineLoop.enter()
        app.onResize { _, _ -> app.renderFrame(listOf("resize intermediate")) }
        app.onUpdate { app.renderFrame(listOf("final update")) }
        app.updateTerminalFrame(0f)
        assertEquals(1, output.size)
        assertEquals("final update", terminalTestRows(terminal, output)[0])
        assertFalse(output.single().contains("resize intermediate"))
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
        val ui =
            UiRoot {
                column =
                    Column(UiStyle(width = UiLength.Fill, height = UiLength.Fill)) {
                        row =
                            Row(UiStyle(width = UiLength.Fill, height = UiLength.Fraction(0.5))) {
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
        assertContains(terminalTestRows(terminal, output)[0]!!, "森林 👩‍💻")
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
        assertEquals("> a", terminalTestRows(terminal, output)[4])
        assertFalse(output.last().contains("late4"))
        prompt.hide()
        app.updateTerminalFrame(0f)
        assertTrue(prompt.isOpen)
        assertEquals("a", prompt.draft)
        assertContains(output.last(), "late4")
        prompt.show()
        app.updateTerminalFrame(0f)
        assertEquals("> a", terminalTestRows(terminal, output)[4])
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
        val prompt =
            CommandPrompt("Console") {
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
        assertEquals(listOf(TerminalUiSpan(1, 0, TextStyles.inverse("x") + "👩‍💻")), spans)
        backend.begin(UiSize(5.0, 2.0))
        backend.end()
        assertTrue(spans.isEmpty())
    }

    @Test
    fun `buttons measure their chrome and paint distinct enabled focused and disabled states`() {
        var spans = emptyList<TerminalUiSpan>()
        val backend = TerminalUiBackend(terminal) { spans = it }
        assertEquals(UiSize(7.0, 3.0), backend.measureButton("Add", 20.0, false))
        val clip = UiRect(0.0, 0.0, 20.0, 9.0)
        backend.begin(UiSize(20.0, 9.0))
        for (y in 0..2) {
            backend.drawButton("Add", UiRect(0.0, y * 3.0, 7.0, 3.0), clip, y == 1, y != 2, false)
        }
        backend.end()
        assertEquals(9, spans.size)
        assertEquals(
            List(3) { listOf("╭─────╮", "│ Add │", "╰─────╯") }.flatten(),
            spans.map { it.text.replace(Regex("\u001b\\[[0-9;]*m"), "") }
        )
        assertTrue(spans[1].text != spans[4].text && spans[4].text != spans[7].text)
    }

    @Test
    fun `Mordant button panels wrap wide labels and clip borders to ancestor bounds`() {
        var spans = emptyList<TerminalUiSpan>()
        val backend = TerminalUiBackend(terminal) { spans = it }
        assertEquals(UiSize(6.0, 4.0), backend.measureButton("界界", 6.0, true))
        val bounds = UiRect(0.0, 0.0, 6.0, 4.0)
        val clip = UiRect(1.0, 1.0, 4.0, 2.0)
        backend.begin(UiSize(6.0, 4.0))
        backend.drawButton("界界", bounds, clip, false, true, true)
        backend.end()
        assertEquals(listOf(1, 2), spans.map { it.y })
        assertTrue(spans.all { it.x == 1 })
        assertEquals(listOf(" 界 ", " 界 "), spans.map { it.text.replace(Regex("\u001b\\[[0-9;]*m"), "") })
        backend.begin(UiSize(6.0, 4.0))
        backend.drawButton("界界", bounds, bounds, false, true, true)
        backend.end()
        assertEquals(
            listOf("╭────╮", "│ 界 │", "│ 界 │", "╰────╯"),
            spans.map { it.text.replace(Regex("\u001b\\[[0-9;]*m"), "") }
        )
    }

    @Test
    fun `narrow and zero width controls stay bounded without panel chrome`() {
        var spans = emptyList<TerminalUiSpan>()
        val backend = TerminalUiBackend(terminal) { spans = it }
        assertEquals(UiSize(0.0, 0.0), backend.measureButton("Add", 0.0, true))
        assertEquals(UiSize(4.0, 1.0), backend.measureButton("A", 4.0, false))
        val bounds = UiRect(0.0, 0.0, 4.0, 1.0)
        backend.begin(UiSize(4.0, 1.0))
        backend.drawButton("A", bounds, bounds, false, true, false)
        backend.drawButton("ignored", UiRect(0.0, 0.0, 0.0, 0.0), bounds, true, true, true)
        backend.end()
        assertEquals("[ A ", spans.single().text.replace(Regex("\u001b\\[[0-9;]*m"), ""))
    }

    @Test
    fun `Mordant controls preserve partial repainting for unchanged and focus only updates`() {
        val screen = TerminalCellScreen(terminal)
        val writes = mutableListOf<String>()
        val backend = TerminalUiBackend(terminal) { spans -> spans.forEach { screen.paint(it.text, it.x, it.y) } }
        val bounds = UiRect(0.0, 0.0, 7.0, 3.0)

        fun render(focused: Boolean) {
            screen.begin(20, 4)
            backend.begin(UiSize(20.0, 4.0))
            backend.drawButton("Add", bounds, bounds, focused, true, false)
            backend.end()
            screen.flush(0 to 3, writes::add)
        }
        render(false)
        val count = writes.size
        render(false)
        assertEquals(count, writes.size)
        render(true)
        assertEquals(count + 1, writes.size)
        assertFalse(writes.last().contains("\u001b[2J"))
        assertContains(writes.last(), "Add")
    }
}

private class UiTestScene : Node<UiTestScene>("Scene")
