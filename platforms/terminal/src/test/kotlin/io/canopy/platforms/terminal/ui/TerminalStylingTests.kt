package io.canopy.platforms.terminal.ui

import kotlin.test.*
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.input.InputFocus
import io.canopy.engine.ui.*
import io.canopy.platforms.terminal.graphics.TerminalSurface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class TerminalStylingTests {
    private val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true)

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `RGB style changes only emit changed cells and equal appearances emit nothing`() {
        // Arrange
        val output = mutableListOf<String>()
        val surface = TerminalSurface(terminal, { Size(21, 5) }, output::add, { false })
        val backend = TerminalUiBackend(terminal, surface::renderUi)
        val scenes = SceneManager()
        val ui = UiManager(backend)
        ManagersRegistry.withScope {
            register(scenes)
            register(InputFocus())
            register(ui)
        }
        lateinit var label: io.canopy.engine.ui.components.Text
        scenes.currScene = UiRoot {
            Column {
                label =
                    Text("color").also {
                        it.style =
                            UiStyle(appearance = UiAppearance(foreground = UiColor.Rgb(255, 0, 0)))
                    }
                Text("stable")
            }
        }
        ui.onResize(21, 5)
        ui.updateFrame()
        output.clear()

        // Act / Assert
        label.style = label.style.copy(appearance = UiAppearance(foreground = UiColor.Rgb(0, 255, 0), bold = true))
        ui.updateFrame()
        assertEquals(1, output.size)
        assertContains(output.single(), "38;2;0;255;0")
        assertFalse(output.single().contains("stable"))
        assertFalse(output.single().contains("\u001b[2J"))
        ui.updateFrame()
        assertEquals(1, output.size)
    }

    @Test
    fun `Mordant border variants and opaque backgrounds respect allocated bounds`() {
        for ((border, top) in listOf(
            UiBorder.Rounded to "╭──╮",
            UiBorder.Square to "┌──┐",
            UiBorder.Double to "╔══╗"
        )) {
            // Arrange
            var spans = emptyList<TerminalUiSpan>()
            val backend = TerminalUiBackend(terminal) { spans = it }
            val bounds = UiRect(0.0, 0.0, 4.0, 3.0)
            backend.begin(UiSize(4.0, 3.0))

            // Act
            backend.drawBox(bounds, bounds, UiAppearance(background = UiColor.Rgb(10, 20, 30)), border)
            backend.end()

            // Assert
            assertEquals(3, spans.size)
            assertEquals(top, spans.first().text.replace(Regex("\u001b\\[[0-9;]*m"), ""))
            assertContains(spans[1].text, "48;2;10;20;30")
        }
    }

    @Test
    fun `oversized child text cannot overwrite a clipping container border`() {
        // Arrange
        var spans = emptyList<TerminalUiSpan>()
        val scenes = SceneManager()
        val ui = UiManager(TerminalUiBackend(terminal) { spans = it })
        ManagersRegistry.withScope {
            register(scenes)
            register(InputFocus())
            register(ui)
        }
        scenes.currScene = UiRoot {
            Box(UiStyle(width = UiLength.Fixed(8.0), height = UiLength.Fixed(3.0), border = UiBorder.Rounded)) {
                Text("01234567890123456789").also { it.style = UiStyle(width = UiLength.Fixed(20.0)) }
            }
        }
        ui.onResize(21, 5)

        // Act
        ui.updateFrame()

        // Assert
        assertEquals("│012345│", spans[1].text.replace(Regex("\u001b\\[[0-9;]*m"), ""))
    }

    @Test
    fun `offscreen panel background and borders allocate only visible cells`() {
        // Arrange
        var spans = emptyList<TerminalUiSpan>()
        val backend = TerminalUiBackend(terminal) { spans = it }
        val clip = UiRect(0.0, 0.0, 4.0, 3.0)
        backend.begin(UiSize(4.0, 3.0))

        // Act
        backend.drawBox(
            UiRect(-1_000_000.0, -1_000_000.0, 1_000_004.0, 1_000_003.0),
            clip,
            UiAppearance(background = UiColor.Rgb(10, 20, 30)),
            UiBorder.Rounded
        )
        backend.end()

        // Assert
        assertEquals(3, spans.size)
        assertEquals("───╯", spans.last().text.replace(Regex("\u001b\\[[0-9;]*m"), ""))
    }

    @Test
    fun `button background covers chrome padding and label`() {
        // Arrange
        var spans = emptyList<TerminalUiSpan>()
        val backend = TerminalUiBackend(terminal) { spans = it }
        val bounds = UiRect(0.0, 0.0, 7.0, 3.0)
        backend.begin(UiSize(7.0, 3.0))

        // Act
        backend.drawButton(
            "Add",
            bounds,
            bounds,
            false,
            true,
            false,
            UiAppearance(background = UiColor.Rgb(10, 20, 30)),
            UiBorder.Rounded
        )
        backend.end()

        // Assert
        val rendered = Text(spans.joinToString("\n") { it.text }).render(terminal, 7)
        assertTrue(rendered.lines.flatten().all { it.style.bgColor != null })
    }

    @Test
    fun `borderless buttons change metrics and explicit empty style bypasses default theme`() {
        // Arrange
        var spans = emptyList<TerminalUiSpan>()
        val backend = TerminalUiBackend(terminal) { spans = it }
        assertEquals(UiSize(5.0, 1.0), backend.measureButton("Add", 20.0, false, UiBorder.None))
        val bounds = UiRect(0.0, 0.0, 5.0, 1.0)
        backend.begin(UiSize(5.0, 1.0))

        // Act
        backend.drawButton("Add", bounds, bounds, true, true, false, UiAppearance.Empty, UiBorder.None)
        backend.end()

        // Assert
        assertEquals(" Add ", spans.single().text)
    }
}
