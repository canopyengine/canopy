package io.canopy.engine.ui

import kotlin.test.*
import io.canopy.engine.core.exceptions.NodeDestroyedException
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.graphics.GridContent
import io.canopy.engine.graphics.GridStyle
import io.canopy.engine.graphics.GridSymbol
import io.canopy.engine.input.InputFocus
import io.canopy.engine.ui.components.Divider
import io.canopy.engine.ui.components.GridNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class GridNodeTests {
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
        override fun measureGrid(columns: Int, rows: Int, columnGap: Int, gridStyle: GridStyle): UiSize {
            measurements++
            return super.measureGrid(columns, rows, columnGap, gridStyle)
        }
        override fun begin(viewport: UiSize) {
            frames++
            painted = emptyList()
            dividerBounds = null
        }
        override fun drawGrid(grid: GridContent, bounds: UiRect, clip: UiRect, appearance: UiAppearance) {
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
        val grid = GridNode(Int.MAX_VALUE, Int.MAX_VALUE)
        val rabbit = GridSymbol('r', UiColor.Rgb(120, 220, 160))

        // Act / Assert
        grid.empty(' ')
        grid[Int.MAX_VALUE - 1, Int.MAX_VALUE - 1] = rabbit
        assertEquals(rabbit, grid[Int.MAX_VALUE - 1, Int.MAX_VALUE - 1])
        assertEquals(GridSymbol(' '), grid[0, 0])
        grid.fill('~')
        assertEquals(GridSymbol('~'), grid[Int.MAX_VALUE - 1, Int.MAX_VALUE - 1])
        grid.clear(0, 0)
        grid.empty('_')
        assertEquals(GridSymbol('_'), grid[0, 0])
        grid.empty(' ')
        assertEquals(GridSymbol(' '), grid[0, 0])
        assertEquals(GridSymbol('~'), grid[1, 0])
        grid.clear()
        assertEquals(GridSymbol(' '), grid[1, 0])
    }

    @Test
    fun `grid style changes layout and default decoration while preserving explicit content`() {
        // Arrange
        lateinit var grid: GridNode<*>
        scenes.currScene = UiRoot { node("map") { GridNode(2, 2).also { grid = it } } }
        grid.set(1, 0, 'r')
        ui.updateFrame()
        val frames = backend.frames
        val measurements = backend.measurements

        // Act
        grid.gridStyle = GridStyle.Solid
        ui.updateFrame()

        // Assert
        assertEquals(UiRect(0.0, 0.0, 7.0, 5.0), backend.gridBounds)
        assertEquals(listOf(' ', 'r', ' ', ' '), backend.painted.map { it.glyph })
        assertEquals(measurements + 1, backend.measurements)
        grid.gridStyle = GridStyle.Solid
        ui.updateFrame()
        assertEquals(frames + 1, backend.frames)
        grid.gridColor = UiColor.Rgb(40, 50, 60)
        ui.updateFrame()
        assertEquals(measurements + 1, backend.measurements)
        assertEquals(GridSymbol(' ', grid.gridColor), grid[0, 0])
        assertEquals(GridSymbol('r'), grid[1, 0])
        grid.empty('_')
        grid.fill('~')
        grid.clear(0, 0)
        grid.gridStyle = GridStyle.Dotted
        ui.updateFrame()
        assertEquals(GridSymbol('_'), grid[0, 0])
        assertEquals(GridSymbol('~'), grid[1, 0])
        assertEquals(UiRect(0.0, 0.0, 3.0, 2.0), backend.gridBounds)
    }

    @Test
    fun `bounds and symbol validation leave existing content intact`() {
        // Arrange
        val grid = GridNode(2, 2)
        grid.set(0, 0, 'r')

        // Act / Assert
        assertFailsWith<IllegalArgumentException> { grid.set(2, 0, 'f') }
        assertFailsWith<IllegalArgumentException> { grid[-1, 0] }
        assertFailsWith<IllegalArgumentException> { grid.clear(0, 2) }
        assertFailsWith<IllegalArgumentException> { grid.columnGap = -1 }
        assertFailsWith<IllegalArgumentException> { GridSymbol('\n') }
        assertFailsWith<IllegalArgumentException> { GridSymbol('界') }
        assertFailsWith<IllegalArgumentException> { GridNode(0, 2) }
        assertEquals(GridSymbol('r'), grid[0, 0])
    }

    @Test
    fun `grid measures itself and batches cells without remeasuring or submitting equal frames`() {
        // Arrange
        lateinit var grid: GridNode<*>
        scenes.currScene = UiRoot { node("map") { GridNode(3, 2).also { grid = it } } }
        ui.updateFrame()
        assertEquals(UiRect(0.0, 0.0, 5.0, 2.0), backend.gridBounds)
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
        grid.columnGap = 2
        ui.updateFrame()
        assertEquals(UiRect(0.0, 0.0, 7.0, 2.0), backend.gridBounds)
        assertEquals(measurements + 1, backend.measurements)
    }

    @Test
    fun `grid inherits border clipping and hidden or destroyed content leaves no live grid`() {
        // Arrange
        lateinit var grid: GridNode<*>
        val root = UiRoot {
            node("map") {
                GridNode(2, 2).also {
                    grid = it
                    it.style = UiStyle(border = UiBorder.Rounded)
                }
            }
        }
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
        lateinit var grid: GridNode<*>
        scenes.currScene = UiRoot { node("map") { GridNode(3, 2).also { grid = it } } }
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
