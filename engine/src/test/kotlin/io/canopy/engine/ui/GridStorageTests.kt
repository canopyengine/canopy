package io.canopy.engine.ui

import kotlin.test.*
import io.canopy.engine.core.exceptions.NodeDestroyedException
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.graphics.GridSymbol
import io.canopy.engine.input.InputFocus
import io.canopy.engine.ui.components.Divider
import io.canopy.engine.ui.components.Grid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class GridStorageTests {
    private lateinit var scenes: SceneManager
    private lateinit var ui: UiManager
    private lateinit var backend: Backend

    private class Backend : UiBackend {
        var frames = 0
        var measurements = 0
        var gridBounds: UiRect? = null
        var gridClip: UiRect? = null
        var painted = emptyList<GridSymbol>()
        var dividerBounds: UiRect? = null
        override fun measureText(text: String, maxWidth: Double, wrap: Boolean) = UiSize(text.length.toDouble(), 1.0)
        override fun measureGridLines(lines: GridLines): GridLineMetrics {
            measurements++
            return super.measureGridLines(lines)
        }
        override fun begin(viewport: UiSize) {
            frames++
            painted = emptyList()
            dividerBounds = null
        }
        override fun drawGridCells(
            grid: GridSurface,
            geometry: GridGeometry,
            bounds: UiRect,
            clip: UiRect,
            appearance: UiAppearance,
        ) {
            gridBounds = bounds
            gridClip = clip
            painted = (0 until grid.rows).flatMap { y -> (0 until grid.columns).map { x -> grid[x, y] } }
        }
        override fun drawDivider(bounds: UiRect, clip: UiRect, appearance: UiAppearance) {
            dividerBounds = bounds
        }
        override fun drawText(
            text: String,
            bounds: UiRect,
            clip: UiRect,
            focused: Boolean,
            wrap: Boolean,
            appearance: UiAppearance,
        ) = Unit
    }

    @BeforeEach
    fun setup() {
        scenes = SceneManager()
        backend = Backend()
        ui = UiManager(backend)
        ManagersRegistry.withScope {
            register(scenes)
            register(InputFocus())
            register(ui)
        }
        ui.onResize(80, 24)
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `cell operations preserve empty configuration and fill does not allocate the entire grid`() {
        // Arrange
        val grid = Grid(columns = Int.MAX_VALUE, rows = Int.MAX_VALUE)
        val rabbit = GridSymbol('r', UiColor.Rgb(120, 220, 160))

        // Act / Assert
        grid.empty(' ')
        grid[Int.MAX_VALUE - 1, Int.MAX_VALUE - 1] = rabbit
        assertEquals(rabbit, grid[Int.MAX_VALUE - 1, Int.MAX_VALUE - 1])
        assertEquals(GridSymbol(' '), grid[0, 0])
        grid.fill('~')
        grid.clear(0, 0)
        grid.empty('_')
        assertEquals(GridSymbol('_'), grid[0, 0])
        grid.empty(' ')
        assertEquals(GridSymbol(' '), grid[0, 0])
        assertEquals(GridSymbol('~'), grid[1, 0])
        grid.clear()
        assertEquals(GridSymbol(' '), grid[1, 0])
    }

    private fun UiScope.board(columns: Int, rows: Int): Grid<*> = Grid(
        columns = columns,
        rows = rows,
        style = UiStyle(gap = 1.0, lineGap = 0.0),
        cellSize = Size(UiLength.Fixed(1.0), UiLength.Fixed(1.0)),
        block = { empty('.') }
    )

    @Test
    fun `line geometry changes sizing while line colors and glyphs retain their independent state`() {
        // Arrange
        lateinit var grid: Grid<*>
        scenes.currScene = UiRoot { grid = board(2, 2) }
        grid.set(1, 0, 'r')
        ui.updateFrame()

        // Act
        grid.cellSize = Size(UiLength.Fixed(2.0), UiLength.Fixed(1.0))
        grid.style = grid.style.copy(gap = 0.0)
        grid.lines = GridLines(outer = LineStyle.Single, inner = LineStyle.Single)
        ui.updateFrame()

        // Assert
        assertEquals(UiRect(0.0, 0.0, 7.0, 5.0), backend.gridBounds)
        assertEquals(listOf('.', 'r', '.', '.'), backend.painted.map { it.glyph })
        val measurements = backend.measurements
        val frames = backend.frames
        grid.lines = grid.lines.copy(outer = grid.lines.outer.copy(color = UiColor.Rgb(40, 50, 60)))
        ui.updateFrame()
        assertEquals(measurements, backend.measurements)
        assertEquals(frames + 1, backend.frames)
        assertEquals(GridSymbol('.'), grid[0, 0])
        assertEquals(GridSymbol('r'), grid[1, 0])
    }

    @Test
    fun `bounds and symbol validation leave existing content intact`() {
        // Arrange
        val grid = Grid(columns = 2, rows = 2)
        grid.set(0, 0, 'r')

        // Act / Assert
        assertFailsWith<IllegalArgumentException> { grid.set(2, 0, 'f') }
        assertFailsWith<IllegalArgumentException> { grid[-1, 0] }
        assertFailsWith<IllegalArgumentException> { grid.clear(0, 2) }
        assertFailsWith<IllegalArgumentException> { grid.style = grid.style.copy(gap = -1.0) }
        assertFailsWith<IllegalArgumentException> { GridSymbol('\n') }
        assertFailsWith<IllegalArgumentException> { GridSymbol('界') }
        assertFailsWith<IllegalArgumentException> { Grid(columns = 0, rows = 2) }
        assertEquals(GridSymbol('r'), grid[0, 0])
    }

    @Test
    fun `grid batches glyph mutations without remeasuring or submitting equal frames`() {
        // Arrange
        lateinit var grid: Grid<*>
        scenes.currScene = UiRoot { grid = board(3, 2) }
        ui.updateFrame()
        val measurements = backend.measurements
        val frames = backend.frames

        // Act
        grid.set(0, 0, 'r')
        grid.set(1, 0, 'f')
        ui.updateFrame()
        grid.set(0, 0, 'r')
        ui.updateFrame()

        // Assert
        assertEquals(measurements, backend.measurements)
        assertEquals(frames + 1, backend.frames)
        assertEquals(listOf('r', 'f', '.', '.', '.', '.'), backend.painted.map { it.glyph })
        grid.style = grid.style.copy(gap = 2.0)
        ui.updateFrame()
        assertEquals(UiRect(0.0, 0.0, 7.0, 2.0), backend.gridBounds)
        assertTrue(backend.measurements > measurements)
    }

    @Test
    fun `grid inherits border clipping and hidden or destroyed content leaves no live grid`() {
        // Arrange
        lateinit var grid: Grid<*>
        val root = UiRoot { grid = board(2, 2).also { it.style = it.style.copy(border = UiBorder.Rounded) } }
        scenes.currScene = root
        ui.updateFrame()

        // Act / Assert
        assertEquals(UiRect(1.0, 1.0, 3.0, 2.0), backend.gridBounds)
        assertEquals(backend.gridBounds, backend.gridClip)
        grid.hide()
        ui.updateFrame()
        assertTrue(backend.painted.isEmpty())
        root.queueFree()
        scenes.onUpdate(0f)
        assertTrue(grid.isFreed)
        assertFailsWith<NodeDestroyedException> { grid[0, 0] }
    }

    @Test
    fun `divider fills available width without taking focus`() {
        // Arrange
        lateinit var divider: Divider<*>
        val root = UiRoot { node("separator") { Divider().also { divider = it } } }
        scenes.currScene = root

        // Act
        ui.updateFrame()

        // Assert
        assertEquals(UiRect(0.0, 0.0, 80.0, 1.0), backend.dividerBounds)
        assertEquals("separator", divider.role)
        assertNull(root.focusedElement)
        assertEquals(UiMouseFilter.Ignore, divider.mouseFilter)
    }

    @Test
    fun `resizing clips presentation without changing grid content or dimensions`() {
        // Arrange
        lateinit var grid: Grid<*>
        scenes.currScene = UiRoot { grid = board(3, 2) }
        grid.set(2, 1, 'r')
        ui.updateFrame()

        // Act
        ui.onResize(3, 1)
        ui.updateFrame()

        // Assert
        assertEquals(UiRect(0.0, 0.0, 3.0, 1.0), backend.gridClip)
        assertEquals(GridSymbol('r'), grid[2, 1])
        assertEquals(3, grid.columns)
        assertEquals(2, grid.rows)
        ui.onResize(80, 24)
        ui.updateFrame()
        assertEquals(UiRect(0.0, 0.0, 5.0, 2.0), backend.gridClip)
    }
}
