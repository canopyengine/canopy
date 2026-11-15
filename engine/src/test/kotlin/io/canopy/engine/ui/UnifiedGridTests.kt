package io.canopy.engine.ui

import kotlin.test.*
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.graphics.GridSymbol
import io.canopy.engine.input.InputFocus
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.MouseButtonEvent
import io.canopy.engine.math.Vector2
import io.canopy.engine.ui.components.Grid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class UnifiedGridTests {
    @Test
    fun `inner line defaults remain inherited through copies and explicit axis overrides remain independent`() {
        // Arrange
        val lines = GridLines(outer = LineStyle.Single, vertical = LineStyle.None)

        // Act
        val changed = lines.copy(inner = LineStyle.Dotted)

        // Assert
        assertEquals(LineStyle.Single, changed.outer)
        assertEquals(LineStyle.Dotted, changed.horizontalStyle)
        assertEquals(LineStyle.None, changed.verticalStyle)
        assertFailsWith<IllegalArgumentException> { LineStyle.Single.copy(width = Double.NaN) }
    }

    private class Backend : UiBackend {
        val draws = mutableListOf<Pair<String, UiRect>>()
        val strokes = mutableListOf<GridLines>()
        override fun begin(viewport: UiSize) {
            draws.clear()
            strokes.clear()
        }
        override fun measureText(text: String, maxWidth: Double, wrap: Boolean) =
            UiSize(if (wrap) minOf(text.length.toDouble(), maxWidth) else text.length.toDouble(), 1.0)
        override fun drawText(
            text: String,
            bounds: UiRect,
            clip: UiRect,
            focused: Boolean,
            wrap: Boolean,
            appearance: UiAppearance,
        ) {
            draws += text to bounds
        }
        override fun drawGridLines(
            lines: GridLines,
            geometry: GridGeometry,
            bounds: UiRect,
            clip: UiRect,
            appearance: UiAppearance,
        ) {
            strokes += lines
        }
    }
    private val backend = Backend()
    private lateinit var scenes: SceneManager
    private lateinit var manager: UiManager

    @BeforeEach
    fun setup() {
        ManagersRegistry.withScope {
            scenes = SceneManager()
            manager = UiManager(backend)
            register(scenes)
            register(manager)
            register(InputFocus())
        }
        manager.onResize(80, 24)
    }

    @AfterEach
    fun cleanup() {
        scenes.currScene = null
        ManagersRegistry.exit()
    }

    @Test
    fun `glyph and UI cells use identical geometry and controls route within reserved line space`() {
        // Arrange
        lateinit var grid: Grid<*>
        var actions = 0
        scenes.currScene = Canvas {
            grid = Grid(
                columns = 2,
                rows = 2,
                cellSize = Size(UiLength.Fixed(4.0), UiLength.Fixed(2.0)),
                lines = GridLines(outer = LineStyle.Single, inner = LineStyle.Dotted),
                block = {
                    empty('.')
                    set(1, 1, '@')
                }
            ) {
                cell(1, 1) { Button("go", onClick = { actions++ }) }
            }
        }

        // Act
        manager.updateFrame()
        val button = backend.draws.last { it.first == "go" }.second
        val glyph = backend.draws.first { it.first == "@" }.second
        val focus = getManager<InputFocus>()
        val hit = focus.route(
            MouseButtonEvent(
                Vector2(button.x.toFloat(), button.y.toFloat()),
                InputBind.LEFT_MOUSE.name,
                InputState.JustPressed
            )
        )

        // Assert
        assertEquals(UiRect(0.0, 0.0, 11.0, 7.0), grid.bounds)
        assertEquals(6.0, glyph.x)
        assertEquals(4.0, glyph.y)
        assertEquals(glyph.x, button.x)
        assertEquals(glyph.y, button.y)
        assertTrue(hit)
        assertEquals(1, actions)
        grid.clear()
        manager.updateFrame()
        assertTrue(backend.draws.any { it.first == "go" })
        assertTrue(backend.draws.none { it.first == "@" })
    }

    @Test
    fun `empty symbols remain independent of outer and internal decoration changes`() {
        // Arrange
        lateinit var grid: Grid<*>
        scenes.currScene = Canvas {
            grid = Grid(columns = 2, rows = 1, block = {
                empty('.')
                set(1, 0, 'x')
            })
        }
        manager.updateFrame()

        // Act
        grid.lines = GridLines(outer = LineStyle.Double, horizontal = LineStyle.Dotted, vertical = LineStyle.None)
        manager.updateFrame()

        // Assert
        assertEquals(GridSymbol('.'), grid[0, 0])
        assertEquals(GridSymbol('x'), grid[1, 0])
        assertEquals(LinePattern.Double, backend.strokes.single().outer.pattern)
        assertEquals(LinePattern.None, backend.strokes.single().verticalStyle.pattern)
    }

    @Test
    fun `sparse huge boards fill in constant space and visit only visible glyph candidates`() {
        // Arrange
        lateinit var grid: Grid<*>
        scenes.currScene = Canvas {
            grid = Grid(
                columns = Int.MAX_VALUE,
                rows = Int.MAX_VALUE,
                cellSize = Size(UiLength.Fixed(1.0), UiLength.Fixed(1.0)),
                block = {
                    fill('~')
                    this[Int.MAX_VALUE - 1, Int.MAX_VALUE - 1] = GridSymbol('@')
                }
            )
        }

        // Act
        manager.updateFrame()

        // Assert
        assertEquals(GridSymbol('@'), grid[Int.MAX_VALUE - 1, Int.MAX_VALUE - 1])
        assertTrue(backend.draws.size <= 80 * 24)
        assertTrue(backend.draws.all { it.first == "~" })
    }

    @Test
    fun `automatic extent includes explicit UI coordinates and glyphs without allocating missing cells`() {
        // Arrange
        lateinit var grid: Grid<*>
        scenes.currScene = Canvas {
            grid = Grid(columns = 3, size = Size.Content, block = { set(0, 4, 'x') }) {
                cell(2, 7) { Text("last") }
            }
        }

        // Act
        manager.updateFrame()

        // Assert
        assertEquals(8, grid.rows)
        assertTrue(backend.draws.any { it.first == "last" && it.second.y == 7.0 })
        grid.clear()
        assertEquals(8, grid.rows)
    }

    @Test
    fun `duplicate explicit cells and mixed placement fail without silent reassignment`() {
        // Arrange / Act / Assert
        val failure = assertFails {
            scenes.currScene = Canvas {
                Grid(columns = 2) {
                    cell(0, 0) {}
                    cell(0, 0) {}
                }
            }
        }
        assertTrue(generateSequence(failure) { it.cause }.any { "Duplicate UI key" in it.message.orEmpty() })
        scenes.currScene = Canvas {
            Grid(columns = 2) {
                cell(0, 0) { Text("explicit") }
                Text("sequential")
            }
        }
        assertFailsWith<IllegalArgumentException> { manager.updateFrame() }
    }

    @Test
    fun `coordinate mutations validate before changing glyph content`() {
        // Arrange
        val grid = Grid(columns = 2, rows = 2)
        grid.set(1, 1, 'x')
        grid.style = UiStyle(border = UiBorder.Square)

        // Act / Assert
        assertFailsWith<IllegalArgumentException> { grid.set(2, 1, 'y') }
        assertFailsWith<IllegalArgumentException> { grid.rowLimit = 1 }
        assertEquals(2, grid.rowLimit)
        assertEquals(2, grid.columns)
        assertEquals(GridSymbol('x'), grid[1, 1])
    }

    @Test
    fun `explicit cells expose their full allocated rectangle for anchored child content`() {
        // Arrange
        scenes.currScene = Canvas {
            Grid(
                columns = 1,
                rows = 1,
                cellSize = Size(UiLength.Fixed(8.0), UiLength.Fixed(5.0)),
                lines = GridLines(outer = LineStyle.Single)
            ) {
                cell(0, 0) { Text("center", place = Place.At(Anchor.Center)) }
            }
        }

        // Act
        manager.updateFrame()

        // Assert
        assertEquals("center" to UiRect(2.0, 3.0, 6.0, 1.0), backend.draws.single())
    }
}
