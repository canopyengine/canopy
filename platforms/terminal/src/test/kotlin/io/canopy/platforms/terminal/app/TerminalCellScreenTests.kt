package io.canopy.platforms.terminal.app

import kotlin.test.*
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal

class TerminalCellScreenTests {
    private val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)
    private val writes = mutableListOf<String>()
    private var viewport = Size(21, 4)
    private val surface = TerminalSurface(terminal, { viewport }, writes::add, { false })

    @Test
    fun `changing one counter writes only the changed cell and leaves other rows untouched`() {
        // Arrange
        surface.renderWorld(listOf("Score: 1", "Unchanged world"))
        writes.clear()
        // Act
        surface.renderWorld(listOf("Score: 2", "Unchanged world"))
        // Assert
        assertEquals(listOf("\u001b[?2026h\u001b[1;8H2\u001b[1;1H\u001b[?2026l"), writes)
    }

    @Test
    fun `shorter content erases only its old tail and unchanged frames write nothing`() {
        surface.renderWorld(listOf("long text"))
        writes.clear()
        surface.renderWorld(listOf("long"))
        assertContains(writes.single(), "\u001b[1;6H    ")
        assertFalse(writes.single().contains("\u001b[2J"))
        surface.renderWorld(listOf("long"))
        assertEquals(1, writes.size)
    }

    @Test
    fun `world and UI changes within a frame commit the final composition once`() {
        surface.renderWorld(listOf("old"))
        writes.clear()
        surface.beginFrame()
        surface.renderWorld(listOf("intermediate"))
        surface.renderUi(listOf(TerminalUiSpan(0, 1, "button")))
        surface.renderWorld(listOf("final"))
        assertTrue(writes.isEmpty())
        surface.endFrame()
        assertEquals(1, writes.size)
        assertContains(writes.single(), "final")
        assertContains(writes.single(), "button")
        assertFalse(writes.single().contains("intermediate"))
    }

    @Test
    fun `covering a wide glyph clears its leading half and hiding overlay restores both cells`() {
        surface.renderWorld(listOf("界z"))
        writes.clear()
        surface.renderUi(listOf(TerminalUiSpan(1, 0, "x")))
        assertContains(writes.single(), "\u001b[1;1H x")
        assertFalse(writes.single().contains("z"))
        writes.clear()
        surface.renderUi(emptyList())
        assertContains(writes.single(), "\u001b[1;1H界")
        assertFalse(writes.single().contains("z"))
    }

    @Test
    fun `style-only changes repaint cells and adjacent styles coalesce`() {
        surface.renderWorld(listOf("\u001b[31mabc\u001b[0m"))
        writes.clear()
        surface.renderWorld(listOf("\u001b[32mabc\u001b[0m"))
        assertContains(writes.single(), "\u001b[32mabc")
        assertEquals(1, Regex("\u001b\\[32m").findAll(writes.single()).count())
        assertFalse(writes.single().contains("\u001b[2J"))
    }

    @Test
    fun `resize clears once and failed partial writes force a complete retry`() {
        var fail = false
        val screen = TerminalCellScreen(terminal)
        fun paint() {
            screen.begin(20, 4)
            screen.paint("hello", 0, 0)
            screen.flush(0 to 0) {
                check(!fail)
                writes.add(it)
            }
        }
        paint()
        screen.begin(20, 4)
        screen.paint("hullo", 0, 0)
        fail = true
        assertFailsWith<IllegalStateException> { screen.flush(0 to 0) { check(!fail) } }
        fail = false
        writes.clear()
        paint()
        assertContains(writes.single(), "\u001b[2J")
        writes.clear()
        screen.begin(10, 2)
        screen.paint("hello", 0, 0)
        screen.flush(0 to 0, writes::add)
        assertContains(writes.single(), "\u001b[2J")
        writes.clear()
        screen.begin(10, 2)
        screen.paint("hello", 0, 0)
        screen.flush(0 to 0, writes::add)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `alternate screen and cursor are restored on exit and line fallback`() {
        surface.openSession()
        surface.closeSession()
        surface.renderWorld(listOf("must not overwrite the restored console"))
        surface.onExit()
        assertEquals(listOf("\u001b[?1049h\u001b[?25l", "\u001b[0m\u001b[?25h\u001b[?1049l"), writes)
    }
}
