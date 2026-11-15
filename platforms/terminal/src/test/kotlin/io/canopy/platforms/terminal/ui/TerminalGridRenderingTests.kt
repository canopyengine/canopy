package io.canopy.platforms.terminal.ui

import kotlin.test.*
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.graphics.GridSymbol
import io.canopy.engine.input.InputFocus
import io.canopy.engine.ui.*
import io.canopy.engine.ui.components.Divider
import io.canopy.engine.ui.components.Grid
import io.canopy.platforms.terminal.graphics.TerminalSurface
import org.junit.jupiter.api.AfterEach

class TerminalGridRenderingTests {
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
        lateinit var grid: Grid<*>
        scenes.currScene = UiRoot {
            Column {
                grid = Grid(columns = 3, rows = 2, style = UiStyle(gap = 1.0, lineGap = 0.0), block = { empty('.') })
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

    private fun geometry(
        backend: TerminalUiBackend,
        columns: Int,
        rows: Int,
        cellSize: io.canopy.engine.ui.Size,
        lines: GridLines = GridLines(),
        gap: Double = 0.0,
    ): GridGeometry {
        lateinit var result: GridGeometry
        val capturing = object : UiBackend by backend {
            override fun drawGridCells(
                grid: GridSurface,
                geometry: GridGeometry,
                bounds: UiRect,
                clip: UiRect,
                appearance: UiAppearance,
            ) {
                result = geometry
            }
        }
        val scenes = SceneManager()
        val manager = UiManager(capturing)
        ManagersRegistry.withScope {
            register(scenes)
            register(InputFocus())
            register(manager)
        }
        manager.onResize(20, 8)
        scenes.currScene = Canvas {
            Grid(
                columns = columns,
                rows = rows,
                cellSize = cellSize,
                lines = lines,
                style = UiStyle(gap = gap, lineGap = 0.0)
            )
        }
        manager.updateFrame()
        return result
    }

    @Test
    fun `huge grid bounds and gaps read only visible cells and inherit clipping`() {
        // Arrange
        var spans = emptyList<TerminalUiSpan>()
        var reads = 0
        val backend = TerminalUiBackend(terminal) { spans = it }
        val geometry = geometry(
            backend,
            Int.MAX_VALUE,
            Int.MAX_VALUE,
            io.canopy.engine.ui.Size(UiLength.Fixed(1.0), UiLength.Fixed(1.0)),
            gap = Int.MAX_VALUE.toDouble()
        )
        val grid = object : GridSurface {
            override val columns = geometry.columns
            override val rows = geometry.rows
            override val lines = GridLines()
            override fun get(x: Int, y: Int): GridSymbol {
                reads++
                return GridSymbol('.')
            }
        }
        backend.begin(UiSize(8.0, 3.0))

        // Act
        backend.drawGridCells(
            grid,
            geometry,
            UiRect(0.0, 0.0, geometry.size.width, geometry.size.height),
            UiRect(0.0, 1.0, 8.0, 1.0),
            UiAppearance.Empty
        )
        backend.end()

        // Assert
        assertEquals(1, reads)
        assertEquals(listOf(TerminalUiSpan(0, 1, ".")), spans)
    }

    private fun plainRows(spans: List<TerminalUiSpan>, startX: Int = 0): List<String> =
        spans.groupBy { it.y }.toSortedMap().values.map { row ->
            val plain = row.map { it.copy(text = it.text.replace(Regex("\u001b\\[[0-9;]*m"), "")) }
            val cells = CharArray(plain.maxOf { it.x + it.text.length }) { ' ' }
            plain.forEach { span -> span.text.forEachIndexed { index, glyph -> cells[span.x + index] = glyph } }
            String(cells).drop(startX).trimEnd()
        }

    @Test
    fun `solid grids draw connected outlines with independent cell colors and clipping`() {
        // Arrange
        var spans = emptyList<TerminalUiSpan>()
        val backend = TerminalUiBackend(terminal) { spans = it }
        val gray = UiColor.Rgb(90, 100, 110)
        val lines = GridLines(outer = LineStyle.Single.copy(color = gray), inner = LineStyle.Single.copy(color = gray))
        val geometry = geometry(
            backend,
            2,
            2,
            io.canopy.engine.ui.Size(UiLength.Fixed(2.0), UiLength.Fixed(1.0)),
            lines
        )
        val grid = Grid(columns = 2, rows = 2, lines = lines)
        grid.set(1, 0, 'r', UiColor.Rgb(120, 220, 160))
        val bounds = UiRect(0.0, 0.0, 7.0, 5.0)
        backend.begin(UiSize(7.0, 5.0))

        // Act
        backend.drawGridCells(grid, geometry, bounds, bounds, UiAppearance.Empty)
        backend.end()

        // Assert
        assertEquals(listOf("┌──┬──┐", "│  │r │", "├──┼──┤", "│  │  │", "└──┴──┘"), plainRows(spans))
        assertTrue(spans.any { "120;220;160" in it.text })
        assertTrue(spans.any { "90;100;110" in it.text })
        backend.begin(UiSize(7.0, 5.0))
        backend.drawGridCells(grid, geometry, bounds, UiRect(3.0, 1.0, 3.0, 2.0), UiAppearance.Empty)
        backend.end()
        assertEquals(listOf("│r", "┼──"), plainRows(spans, startX = 3))
        assertTrue(spans.all { it.x >= 3 })
    }

    @Test
    fun `solid grids with huge dimensions and gaps read only visible cell anchors`() {
        // Arrange
        var spans = emptyList<TerminalUiSpan>()
        var reads = 0
        val backend = TerminalUiBackend(terminal) { spans = it }
        val lines = GridLines(outer = LineStyle.Single, inner = LineStyle.Single)
        val geometry = geometry(
            backend,
            Int.MAX_VALUE,
            Int.MAX_VALUE,
            io.canopy.engine.ui.Size(UiLength.Fixed(Int.MAX_VALUE.toDouble() + 1.0), UiLength.Fixed(1.0)),
            lines
        )
        val grid = object : GridSurface {
            override val columns = geometry.columns
            override val rows = geometry.rows
            override val lines = lines
            override fun get(x: Int, y: Int): GridSymbol {
                reads++
                return GridSymbol('r')
            }
        }
        backend.begin(UiSize(8.0, 3.0))

        // Act
        backend.drawGridCells(
            grid,
            geometry,
            UiRect(0.0, 0.0, geometry.size.width, geometry.size.height),
            UiRect(0.0, 1.0, 8.0, 1.0),
            UiAppearance.Empty
        )
        backend.end()

        // Assert
        assertEquals(1, reads)
        assertEquals(listOf("│r"), plainRows(spans))
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
