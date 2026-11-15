package io.canopy.platforms.terminal.ui

import kotlin.test.*
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.input.InputFocus
import io.canopy.engine.ui.*
import io.canopy.engine.ui.components.Grid
import org.junit.jupiter.api.AfterEach

class UnifiedTerminalGridTests {
    private val terminal = Terminal(ansiLevel = AnsiLevel.NONE, interactive = false)
    private lateinit var manager: UiManager
    private lateinit var scenes: SceneManager
    private var spans = emptyList<TerminalUiSpan>()

    private fun setup(content: UiScope.() -> Unit) {
        val backend = TerminalUiBackend(terminal) { spans = it }
        ManagersRegistry.withScope {
            scenes = SceneManager()
            manager = UiManager(backend)
            register(scenes)
            register(manager)
            register(InputFocus())
        }
        manager.onResize(30, 12)
        scenes.currScene = Canvas(content = content)
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    private fun rows(): List<String> = spans.groupBy { it.y }.toSortedMap().values.map { row ->
        val cells = CharArray(row.maxOf { it.x + it.text.length }) { ' ' }
        row.forEach { span -> span.text.forEachIndexed { offset, glyph -> cells[span.x + offset] = glyph } }
        String(cells)
    }

    @Test
    fun `fractional fill cell edges reserve the same terminal pixels for lines and UI content`() {
        // Arrange
        setup {
            Grid(columns = 3, lines = GridLines(outer = LineStyle.Single, inner = LineStyle.Single)) {
                Text("A")
                Text("B")
                Text("C")
            }
        }
        manager.onResize(16, 6)

        // Act
        manager.updateFrame()

        // Assert
        assertEquals("│A  │B   │C   │", rows()[1])
    }

    @Test
    fun `solid outer border surrounds independently dotted internal dividers`() {
        // Arrange
        setup {
            Grid(
                columns = 2,
                rows = 2,
                cellSize = Size(UiLength.Fixed(3.0), UiLength.Fixed(1.0)),
                lines = GridLines(outer = LineStyle.Single, inner = LineStyle.Dotted),
                block = {
                    set(0, 0, 'A')
                    set(1, 0, 'B')
                    set(0, 1, 'C')
                    set(1, 1, 'D')
                }
            )
        }

        // Act
        manager.updateFrame()

        // Assert
        assertEquals(listOf("┌───┬───┐", "│A   B  │", "├ . . . ┤", "│C   D  │", "└───┴───┘"), rows())
    }

    @Test
    fun `horizontal and vertical dividers can be enabled independently and empty dots are content`() {
        // Arrange
        setup {
            Grid(
                columns = 2,
                rows = 2,
                cellSize = Size(UiLength.Fixed(2.0), UiLength.Fixed(1.0)),
                lines = GridLines(outer = LineStyle.Double, horizontal = LineStyle.Single),
                block = { empty('.') }
            )
        }

        // Act
        manager.updateFrame()

        // Assert
        assertEquals(listOf("╔════╗", "║. . ║", "╠────╣", "║. . ║", "╚════╝"), rows())
    }

    @Test
    fun `rounded outer corners and UI controls occupy reserved content rectangles`() {
        // Arrange
        lateinit var grid: Grid<*>
        setup {
            grid = Grid(
                columns = 1,
                rows = 1,
                cellSize = Size(UiLength.Fixed(4.0), UiLength.Fixed(1.0)),
                lines = GridLines(outer = LineStyle.Rounded)
            ) {
                cell(0, 0) { Text("UI") }
            }
        }

        // Act
        manager.updateFrame()

        // Assert
        assertEquals(listOf("╭────╮", "│UI  │", "╰────╯"), rows())
        assertEquals(UiRect(0.0, 0.0, 6.0, 3.0), grid.bounds)
    }

    @Test
    fun `line thickness resolves to terminal cells and clipped huge grids remain viewport bounded`() {
        // Arrange
        setup {
            Grid(
                columns = Int.MAX_VALUE,
                rows = Int.MAX_VALUE,
                cellSize = Size(UiLength.Fixed(1.0), UiLength.Fixed(1.0)),
                lines = GridLines(outer = LineStyle.Double.copy(width = 20.0), inner = LineStyle.Single),
                block = { fill('x') }
            )
        }

        // Act
        manager.updateFrame()

        // Assert
        assertTrue(spans.size <= 12)
        assertTrue(spans.all { it.x >= 0 && it.y < 12 && it.text.length <= 29 })
        assertTrue(rows().first().startsWith("╔"))
        assertTrue(rows().any { 'x' in it })
    }
}
