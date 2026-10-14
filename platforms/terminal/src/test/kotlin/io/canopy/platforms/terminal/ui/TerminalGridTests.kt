package io.canopy.platforms.terminal.ui

import kotlin.test.*
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.graphics.GridContent
import io.canopy.engine.graphics.GridSymbol
import io.canopy.engine.input.InputFocus
import io.canopy.engine.ui.*
import io.canopy.engine.ui.components.Divider
import io.canopy.engine.ui.components.GridNode
import io.canopy.platforms.terminal.graphics.TerminalSurface
import org.junit.jupiter.api.AfterEach

class TerminalGridTests {
    private val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = false)

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `grid movement is batched and only changed cells reach terminal output`() {
        // Arrange
        val writes = mutableListOf<String>()
        val surface = TerminalSurface(terminal, { Size(20, 8) }, writes::add, { false })
        val ui = UiManager(TerminalUiBackend(terminal, surface::renderUi))
        val scenes = SceneManager()
        ManagersRegistry.withScope {
            register(scenes)
            register(InputFocus())
            register(ui)
        }
        lateinit var grid: GridNode
        scenes.currScene = UiRoot {
            Column {
                node("grid") { GridNode(3, 2).also { grid = it } }
                node("divider") { Divider() }
            }
        }
        ui.onResize(20, 8)
        val rabbit = GridSymbol('r', UiColor.Rgb(120, 220, 160))
        grid[0, 0] = rabbit
        ui.updateFrame()
        writes.clear()

        // Act
        grid.clear(0, 0)
        grid[1, 0] = rabbit
        ui.updateFrame()

        // Assert
        assertEquals(1, writes.size)
        assertContains(writes.single(), "r")
        assertContains(writes.single(), "120;220;160")
        assertFalse(writes.single().contains("\u001b[2J"))
        assertFalse(writes.single().contains("─"))
        writes.clear()
        ui.updateFrame()
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `huge grid bounds and gaps read only visible cells and inherit clipping`() {
        // Arrange
        var spans = emptyList<TerminalUiSpan>()
        var reads = 0
        val grid = object : GridContent {
            override val columns = Int.MAX_VALUE
            override val rows = Int.MAX_VALUE
            override val columnGap = Int.MAX_VALUE
            override fun get(x: Int, y: Int): GridSymbol {
                reads++
                return GridSymbol('.')
            }
        }
        val backend = TerminalUiBackend(terminal) { spans = it }
        backend.begin(UiSize(8.0, 3.0))

        // Act
        backend.drawGrid(grid, UiRect(0.0, 0.0, 1e18, 1e18), UiRect(0.0, 1.0, 8.0, 1.0), UiAppearance.Empty)
        backend.end()

        // Assert
        assertEquals(1, reads)
        assertEquals(listOf(TerminalUiSpan(0, 1, ".")), spans)
        assertEquals(UiSize(5.0, 2.0), backend.measureGrid(3, 2, 1))
    }

    @Test
    fun `dividers paint one bounded styled line and ignore empty clips`() {
        // Arrange
        var spans = emptyList<TerminalUiSpan>()
        val backend = TerminalUiBackend(terminal) { spans = it }
        backend.begin(UiSize(8.0, 3.0))

        // Act
        backend.drawDivider(
            UiRect(0.0, 1.0, 1e18, 1.0),
            UiRect(2.0, 1.0, 4.0, 1.0),
            UiAppearance(foreground = UiColor.Rgb(100, 110, 120))
        )
        backend.drawDivider(UiRect(0.0, 0.0, 8.0, 1.0), UiRect(0.0, 0.0, 0.0, 0.0), UiAppearance.Empty)
        backend.end()

        // Assert
        assertEquals(1, spans.size)
        assertEquals(2, spans.single().x)
        assertEquals(1, spans.single().y)
        assertEquals("────", spans.single().text.replace(Regex("\u001b\\[[0-9;]*m"), ""))
        assertContains(spans.single().text, "100;110;120")
    }
}
