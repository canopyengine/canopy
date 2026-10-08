package io.canopy.platforms.terminal.app

import kotlin.test.*
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import io.canopy.adapters.mordant.input.MordantInputManager
import io.canopy.engine.app.Screen
import io.canopy.engine.app.screens
import io.canopy.engine.commands.CommandPrompt
import io.canopy.engine.commands.CommandPromptHost
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.input.InputFocus
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.math.Vector2
import io.canopy.engine.ui.UiManager
import org.junit.jupiter.api.AfterEach

class TerminalFrameRestorationTests {
    private val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)
    private val output = mutableListOf<String>()
    private var viewport = Size(20, 6)
    private var app: TerminalApp? = null

    @AfterEach
    fun cleanup() {
        app?.screens { }
        app?.engineLoop?.exit()
        ManagersRegistry.exit()
    }

    @Test
    fun `world stays live above bottom panel and close restores copied latest world`() {
        // Arrange: real installed host and captured surface, without a TTY or background input reader.
        val (app, host, prompt) = start()
        app.commandPanelRows = 3
        app.renderFrame(listOf("old", "second", "third", "fourth"))
        prompt.open()
        prompt.command("say") { execute { reply("first\nsecond\nnewest") } }
        prompt.submit("say")
        host.onUpdate(0f)

        // Act
        val latest = mutableListOf("latest", "world two", "world three", "world four")
        app.renderFrame(latest)
        latest[0] = "caller mutation"

        // Assert: newest physical transcript rows and editor occupy the bottom, without stopping world output.
        assertEquals(
            mapOf(0 to "latest", 1 to "world two", 2 to "world three", 3 to "second", 4 to "newest", 5 to "> "),
            rows()
        )
        prompt.close()
        host.onUpdate(0f)
        assertEquals(mapOf(0 to "latest", 1 to "world two", 2 to "world three", 3 to "world four"), rows())
        val count = output.size
        host.onUpdate(0f)
        assertEquals(count, output.size)
    }

    @Test
    fun `command typing captures mapped actions while real engine frames continue`() {
        // Arrange
        val (app, host, prompt) = start()
        val input = ManagersRegistry.getManager(MordantInputManager::class)
        input.mapActions("jump" to listOf(InputBind.A))
        app.onUpdate { app.renderFrame(listOf("frame ${app.frameCount}")) }
        prompt.open()
        host.onUpdate(0f)

        // Act
        input.enqueueMordantKeyEvent(KeyboardEvent("a"))
        input.processEvents()
        app.engineLoop.update(0.02f)
        app.engineLoop.update(0.02f)

        // Assert
        assertEquals("a", prompt.draft)
        assertFalse(input.isActionPressed("jump"))
        assertTrue(input.eventsThisFrame.isEmpty())
        assertFalse(app.isPaused)
        assertEquals(2L, app.frameCount)
        assertEquals("frame 2", rows()[0])
        assertEquals("> a", rows()[5])
    }

    @Test
    fun `command close retains whole-frame input capture and next frame releases gameplay`() {
        val (_, host, prompt) = start()
        val input = ManagersRegistry.getManager(MordantInputManager::class)
        input.mapActions("jump" to listOf(InputBind.A))
        prompt.command("close") { execute { prompt.close() } }
        prompt.open()
        host.onUpdate(0f)
        "close".forEach { input.enqueueMordantKeyEvent(KeyboardEvent(it.toString())) }
        input.enqueueMordantKeyEvent(KeyboardEvent("Enter"))
        input.enqueueMordantKeyEvent(KeyboardEvent("a"))
        input.processEvents()
        assertFalse(prompt.isOpen)
        assertFalse(input.isActionPressed("jump"))
        assertTrue(input.eventsThisFrame.isEmpty())
        input.enqueueMordantKeyEvent(KeyboardEvent("a"))
        input.processEvents()
        assertTrue(input.isActionPressed("jump"))
    }

    @Test
    fun `closing paused prompt and tree exit restore retained and empty frames`() {
        val (app, host, prompt) = start()
        app.renderFrame(listOf("paused world"))
        app.pause()
        prompt.open()
        host.onUpdate(0f)
        prompt.close()
        host.onUpdate(0f)
        assertEquals(mapOf(0 to "paused world"), rows())
        assertTrue(app.isPaused)
        assertEquals(0L, app.frameCount)
        prompt.open()
        host.onUpdate(0f)
        app.renderFrame(emptyList())
        ManagersRegistry.getManager(SceneManager::class).currScene = null
        assertEquals(emptyMap(), rows())
    }

    @Test
    fun `resize recomposes unchanged snapshot and narrow or empty viewport cannot scroll`() {
        val (app, host, prompt) = start()
        app.commandPanelRows = 2
        app.renderFrame(listOf("abcdefghijk", "extra"))
        prompt.open()
        host.onUpdate(0f)
        viewport = Size(5, 3)
        host.onUpdate(0f)
        assertEquals(mapOf(0 to "abcd", 2 to "> "), rows())
        assertTrue(output.last().none { it == '\n' || it == '\r' })
        viewport = Size(5, 1)
        host.onUpdate(0f)
        assertEquals(mapOf(0 to "> "), rows())
        viewport = Size(1, 1)
        host.onUpdate(0f)
        assertEquals(emptyMap(), rows())
        viewport = Size(0, 0)
        host.onUpdate(0f)
        assertEquals("\u001b[2J\u001b[H", output.last())
        assertFailsWith<IllegalArgumentException> { app.commandPanelRows = 0 }
    }

    @Test
    fun `unicode and tabs use terminal cell widths and controls cannot escape rows`() {
        val (app, host, prompt) = start()
        viewport = Size(7, 4)
        app.commandPanelRows = 2
        app.renderFrame(listOf("界😀e\u0301more", "x\tZ\u001b[2J\r\u0007"))
        prompt.open()
        host.onUpdate(0f)
        val physical = rows()
        assertEquals("界😀e\u0301m", physical[0])
        assertTrue(physical.values.all { Text(it, whitespace = Whitespace.PRE).measure(terminal, 100).max <= 6 })
        assertFalse(output.last().contains('\t'))
        assertFalse(output.last().contains('\r'))
        assertFalse(output.last().contains('\u0007'))
        assertEquals(1, Regex("\u001b\\[2J").findAll(output.last()).count())
    }

    @Test
    fun `world SGR styling is preserved without allowing cursor sequences`() {
        val (app, _, _) = start()
        app.renderFrame(listOf("\u001b[31mred\u001b[0m\u001b[99;99H"))
        assertTrue(output.last().contains("\u001b[31m"))
        assertFalse(output.last().contains("\u001b[99;99H"))
        assertTrue(rows()[0]!!.startsWith("red"))
    }

    @Test
    fun `failed close releases ownership and retries the same retained frame`() {
        var fail = false
        val (app, host, prompt) = start { text ->
            check(!fail) { "output unavailable" }
            output.add(text)
        }
        prompt.open()
        host.onUpdate(0f)
        app.renderFrame(listOf("retained"))
        fail = true
        prompt.close()
        assertFailsWith<IllegalStateException> { host.onUpdate(0f) }
        fail = false
        app.renderFrame(listOf("retained"))
        assertEquals(mapOf(0 to "retained"), rows())
    }

    @Test
    fun `failed open retries unchanged snapshot on next host frame`() {
        var fail = true
        val (_, host, prompt) = start { text ->
            check(!fail) { "output unavailable" }
            output.add(text)
        }
        prompt.open()
        assertFailsWith<IllegalStateException> { host.onUpdate(0f) }
        fail = false
        host.onUpdate(0f)
        assertEquals(mapOf(5 to "> "), rows())
    }

    @Test
    fun `long editor scrolls whole Unicode graphemes and backspace reveals preceding tail`() {
        val (app, host, prompt) = start()
        viewport = Size(9, 3)
        app.commandPanelRows = 1
        val input = ManagersRegistry.getManager(MordantInputManager::class)
        prompt.open()
        "012345".forEach { input.enqueueMordantKeyEvent(KeyboardEvent(it.toString())) }
        input.enqueueMordantKeyEvent(KeyboardEvent("界"))
        input.enqueueMordantKeyEvent(KeyboardEvent("😀"))
        input.enqueueMordantKeyEvent(KeyboardEvent("e"))
        input.enqueueMordantKeyEvent(KeyboardEvent("\u0301"))
        input.processEvents()
        host.onUpdate(0f)
        assertEquals("012345界😀e\u0301", prompt.draft)
        assertEquals("> 5界😀e\u0301", rows()[2])
        input.enqueueMordantKeyEvent(KeyboardEvent("Backspace"))
        input.processEvents()
        host.onUpdate(0f)
        // Editor backspace retains its existing code-point behavior; horizontal scrolling itself never splits clusters.
        assertEquals("> 5界😀e", rows()[2])
        viewport = Size(3, 1)
        host.onUpdate(0f)
        assertEquals("e", rows()[0])
        prompt.close()
        host.onUpdate(0f)
        assertEquals("012345界😀e", prompt.draft)
    }

    @Test
    fun `editor tail never cuts a joined emoji grapheme and cursor tracks empty editor`() {
        val (app, host, prompt) = start()
        viewport = Size(9, 3)
        app.commandPanelRows = 1
        prompt.open()
        host.onUpdate(0f)
        assertTrue(output.last().endsWith("\u001b[3;3H"))
        prompt.prompt = ">   "
        host.onUpdate(0f)
        assertTrue(output.last().endsWith("\u001b[3;5H"))
        prompt.prompt = ""
        host.onUpdate(0f)
        assertTrue(output.last().endsWith("\u001b[3;1H"))
        val input = ManagersRegistry.getManager(MordantInputManager::class)
        val draft = "abcdefgh👩‍💻"
        draft.codePoints().toArray().forEach {
            input.enqueueMordantKeyEvent(KeyboardEvent(String(Character.toChars(it))))
        }
        input.processEvents()
        host.onUpdate(0f)
        assertEquals(draft, prompt.draft)
        assertTrue(rows()[2]!!.endsWith("👩‍💻"))
        assertFalse(rows()[2]!!.startsWith("\u200d"))
        assertTrue(Text(rows()[2]!!, whitespace = Whitespace.PRE).measure(terminal, 100).max <= 8)
    }

    @Test
    fun `whitespace rich world row clips without producing line breaks or scrolling`() {
        val (app, _, _) = start()
        viewport = Size(7, 2)
        app.renderFrame(listOf("one two three four five", "x\tend"))
        assertEquals("one tw", rows()[0])
        assertTrue(output.last().none { it == '\n' || it == '\r' || it == '\t' })
        assertTrue(rows().values.all { Text(it, whitespace = Whitespace.PRE).measure(terminal, 100).max <= 6 })
    }

    @Test
    fun `line fallback suppresses live surface updates and closes without raw cursor output`() {
        var line = false
        val writes = mutableListOf<String>()
        val surface = TerminalSurface(terminal, { viewport }, writes::add, { line })
        val presentation =
            TerminalCommandPresentation(
                { line },
                writes::add,
                TerminalCommandUi(terminal, { viewport }, surface, { 2 }, { 1.0 })
            )
        val host = CommandPromptHost(terminalApp(), presentation)
        val scenes = SceneManager()
        ManagersRegistry.withScope {
            register(InputFocus())
            register(host)
            register(scenes)
            register(UiManager())
        }
        val prompt = CommandPrompt("Console")
        scenes.currScene = prompt
        prompt.open()
        host.onUpdate(0f)
        line = true
        writes.clear()
        surface.renderWorld(listOf("live but suppressed"))
        host.onUpdate(0f)
        assertEquals(listOf("> "), writes)
        prompt.close()
        host.onUpdate(0f)
        surface.renderWorld(listOf("still suppressed"))
        assertEquals(listOf("> ", "\n"), writes)
        assertTrue(writes.none { '\u001b' in it })
    }

    @Test
    fun `native frames resize responsive screen and scene before update without replacing callbacks`() {
        val geometries = mutableListOf<Size>()
        val sceneSizes = mutableListOf<Vector2>()
        var updates = 0
        lateinit var target: TerminalApp
        val screen = object : Screen() {
            override fun onResize(width: Int, height: Int) {
                geometries.add(Size(width, height))
                // A responsive screen computes its own layout from full console cell dimensions.
                target.renderFrame(List(height) { "x".repeat((width / 2).coerceAtLeast(0)) })
            }
        }
        val (app, _, _) = start(configure = {
            target = this
            screens { start(screen) }
            onUpdate { updates++ }
            onResize { width, height ->
                assertEquals(
                    Vector2(width.toFloat(), height.toFloat()),
                    ManagersRegistry.getManager(SceneManager::class).sceneSize()
                )
            }
        })
        val scenes = ManagersRegistry.getManager(SceneManager::class)
        val resizeConnection = scenes.onResize.connect { _, _ -> sceneSizes.add(scenes.sceneSize()) }
        app.updateTerminalFrame(0f)
        assertEquals("x".repeat(10), rows()[0])
        app.updateTerminalFrame(0f)
        viewport = Size(8, 3)
        app.updateTerminalFrame(0f)
        assertEquals(mapOf(0 to "xxxx", 1 to "xxxx", 2 to "xxxx"), rows())
        viewport = Size(12, 9)
        app.pause()
        app.updateTerminalFrame(0f)
        assertEquals(9, rows().size)
        assertEquals("xxxxxx", rows()[8])
        assertEquals(listOf(20 to 6, 8 to 3, 12 to 9), geometries.map { it.width to it.height })
        assertEquals(listOf(Vector2(20f, 6f), Vector2(8f, 3f), Vector2(12f, 9f)), sceneSizes)
        assertEquals(4, updates)
        assertTrue(app.isPaused)
        resizeConnection.disconnect()
    }

    @Test
    fun `closed static world refreshes geometry even when clipped text is unchanged and paused`() {
        val (app, _, _) = start()
        app.renderFrame(listOf("static"))
        app.updateTerminalFrame(0f)
        val count = output.size
        app.pause()
        viewport = Size(30, 12)
        app.updateTerminalFrame(0f)
        assertEquals(count + 1, output.size)
        assertEquals(mapOf(0 to "static"), rows())
        app.updateTerminalFrame(0f)
        assertEquals(count + 1, output.size)
        assertTrue(app.isPaused)
    }

    @Test
    fun `adaptive panel grows shrinks and caps while preserving command focus draft and transcript`() {
        val (app, host, prompt) = start()
        app.commandPanelHeightFraction = 1.0 / 3.0
        app.renderFrame(List(30) { "world$it" })
        prompt.command("say") { execute { reply("one\ntwo\nthree\nfour\nfive\nsix\nseven\neight") } }
        prompt.submit("say")
        prompt.open()
        val input = ManagersRegistry.getManager(MordantInputManager::class)
        input.enqueueMordantKeyEvent(KeyboardEvent("界"))
        input.processEvents()
        app.updateTerminalFrame(0f)
        assertEquals("eight", rows()[4])
        assertEquals("> 界", rows()[5])
        viewport = Size(30, 12)
        app.updateTerminalFrame(0f)
        assertEquals("six", rows()[8])
        assertEquals("eight", rows()[10])
        viewport = Size(40, 30)
        app.pause()
        app.updateTerminalFrame(0f)
        assertEquals("two", rows()[22])
        assertEquals("> 界", rows()[29])
        viewport = Size(3, 1)
        app.updateTerminalFrame(0f)
        assertEquals("界", rows()[0])
        assertEquals("界", prompt.draft)
        assertTrue(prompt.isOpen)
        assertTrue(app.isPaused)
        viewport = Size(20, 6)
        app.updateTerminalFrame(0f)
        assertEquals("> 界", rows()[5])
        val count = output.size
        host.onUpdate(0f)
        app.updateTerminalFrame(0f)
        assertEquals(count, output.size)
    }

    @Test
    fun `fraction validation rejects nonfinite and out of range values and supports full-height cap`() {
        val (app, _, prompt) = start()
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -0.1, 1.1).forEach {
            assertFailsWith<IllegalArgumentException> { app.commandPanelHeightFraction = it }
        }
        app.commandPanelHeightFraction = 1.0
        app.commandPanelRows = 100
        app.renderFrame(List(6) { "world" })
        prompt.open()
        app.updateTerminalFrame(0f)
        assertEquals(mapOf(0 to "world", 5 to "> "), rows())
    }

    @Test
    fun `resize output can retry but shutdown retains the original failure`() {
        var fail = false
        val (app, _, _) = start { text ->
            check(!fail) { "output unavailable" }
            output.add(text)
        }
        app.renderFrame(listOf("same"))
        app.updateTerminalFrame(0f)
        viewport = Size(30, 12)
        fail = true
        val failure = assertFailsWith<IllegalStateException> { app.updateTerminalFrame(0f) }
        fail = false
        val count = output.size
        app.updateTerminalFrame(0f)
        assertEquals(count + 1, output.size)
        assertEquals(mapOf(0 to "same"), rows())
        assertSame(failure, assertFailsWith<IllegalStateException> { app.fail(failure) })
    }

    @Test
    fun `failed resize callback retries before dispatch and shutdown retains failure`() {
        var fail = true
        var resizes = 0
        var updates = 0
        val (app, _, _) = start(configure = {
            onResize { _, _ ->
                resizes++
                check(!fail) { "layout unavailable" }
            }
            onUpdate { updates++ }
        })
        val failure = assertFailsWith<IllegalStateException> { app.updateTerminalFrame(0f) }
        assertEquals(0, updates)
        fail = false
        app.updateTerminalFrame(0f)
        app.updateTerminalFrame(0f)
        assertEquals(2, resizes)
        assertEquals(2, updates)
        assertSame(failure, assertFailsWith<IllegalStateException> { app.fail(failure) })
    }

    @Test
    fun `native line fallback ignores viewport changes and never emits raw repaint`() {
        var resizes = 0
        val (app, _, prompt) = start(configure = { onResize { _, _ -> resizes++ } })
        app.renderFrame(listOf("world"))
        app.updateTerminalFrame(0f)
        app.prepareLineInputPresentation()
        output.clear()
        prompt.open()
        viewport = Size(4, 2)
        app.updateTerminalFrame(0f)
        assertEquals(listOf("> "), output)
        assertEquals(1, resizes)
        assertTrue(output.none { '\u001b' in it })
    }

    /** Decode positions and styles into physical rows; bounds and cell widths are asserted separately. */
    private fun rows(): Map<Int, String> {
        val cells = mutableMapOf<Int, MutableMap<Int, String>>()
        var row = 0
        var column = 0
        val clean = output.last().replace(Regex("\u001b\\[[0-9;:]*m"), "")
        val pattern = Regex("\u001b\\[([0-9;]*)([A-Za-z])|([^\u001b]+)")
        pattern.findAll(clean).forEach { match ->
            when (match.groupValues[2]) {
                "J" -> cells.clear()
                "H" -> {
                    row = (match.groupValues[1].substringBefore(';').toIntOrNull() ?: 1) - 1
                    column = (match.groupValues[1].substringAfter(';', "1").toIntOrNull() ?: 1) - 1
                }
                "" -> Regex("\\X").findAll(match.groupValues[3]).forEach { cluster ->
                    val width = Text(cluster.value, whitespace = Whitespace.PRE).measure(terminal, 100).max
                    val line = cells.getOrPut(row) { mutableMapOf() }
                    line[column] = cluster.value
                    for (offset in 1 until width) line[column + offset] = ""
                    column += width
                }
            }
        }
        return cells.mapNotNull { (index, line) ->
            val text = (0..(line.keys.maxOrNull() ?: -1)).joinToString("") { line[it] ?: " " }.trimEnd()
            if (text.isEmpty()) {
                null
            } else {
                index to if (index == row && output.last().endsWith('H')) {
                    val width = Text(text, whitespace = Whitespace.PRE).measure(terminal, 100).max
                    text + " ".repeat((column - width).coerceAtLeast(0))
                } else {
                    text
                }
            }
        }.toMap()
    }

    private fun start(
        configure: TerminalApp.() -> Unit = {},
        write: (String) -> Unit = output::add,
    ): Triple<TerminalApp, CommandPromptHost, CommandPrompt> {
        val app = TerminalApp(terminal, write, { viewport })
        app.commandPanelHeightFraction = 1.0
        this.app = app
        app.configure()
        app.engineLoop.enter()
        val host = ManagersRegistry.getManager(CommandPromptHost::class)
        val prompt = CommandPrompt("Console")
        ManagersRegistry.getManager(SceneManager::class).currScene = prompt
        return Triple(app, host, prompt)
    }
}
