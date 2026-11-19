package io.canopy.engine.ui

import kotlin.test.*
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.graphics.DrawContext
import io.canopy.engine.input.InputFocus
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.MouseButtonEvent
import io.canopy.engine.math.Vector2
import io.canopy.engine.ui.components.Button
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class CanvasLayoutTests {
    private lateinit var scenes: SceneManager
    private lateinit var manager: UiManager
    private class Backend(val glyph: Double = 1.0) : UiBackend {
        val draws = mutableListOf<Pair<String, UiRect>>()
        var frames = 0
        override fun begin(viewport: UiSize) {
            frames++
            draws.clear()
        }
        override fun measureText(text: String, maxWidth: Double, wrap: Boolean): UiSize =
            UiSize(if (wrap) minOf(text.length * glyph, maxWidth) else text.length * glyph, glyph)
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
    }
    private val backend = Backend()

    @BeforeEach
    fun setup() {
        ManagersRegistry.withScope {
            scenes = SceneManager()
            manager = UiManager(backend)
            register(scenes)
            register(InputFocus())
            register(manager)
        }
        manager.onResize(80, 24)
    }

    @AfterEach
    fun cleanup() {
        scenes.currScene = null
        ManagersRegistry.exit()
    }

    @Test
    fun `canvas visibility toggle clears focus and restores retained controls without recomposing`() {
        // Arrange
        lateinit var button: Button<*>
        var compositions = 0
        val canvas = Canvas {
            compositions++
            button = Button("target", onClick = {})
        }
        scenes.currScene = canvas
        manager.updateFrame()
        canvas.focus(button)
        val original = button
        val bounds = button.bounds

        // Act / Assert
        canvas.toggleVisibility()
        manager.updateFrame()
        assertTrue(backend.draws.isEmpty())
        assertNull(canvas.focusedElement)
        assertTrue(button.isVisible)
        assertTrue(button.isInsideTree)

        canvas.toggleVisibility()
        manager.updateFrame()
        assertEquals("target", backend.draws.single().first)
        assertEquals(bounds, button.bounds)
        assertSame(original, button)
        assertEquals(1, compositions)
    }

    @Test
    fun `anchors use backend measured sizes and follow viewport resize`() {
        // Arrange
        val canvas = Canvas { Text("test", place = Place.At(Anchor.Center)) }
        scenes.currScene = canvas

        // Act / Assert
        manager.updateFrame()
        assertEquals(UiRect(38.0, 11.5, 4.0, 1.0), backend.draws.single().second)
        manager.onResize(100, 30)
        manager.updateFrame()
        assertEquals(UiRect(48.0, 14.5, 4.0, 1.0), backend.draws.single().second)
        val graphical = Backend(2.0)
        canvas.render(graphical, UiSize(100.0, 30.0))
        assertEquals(UiRect(46.0, 14.0, 8.0, 2.0), graphical.draws.single().second)
    }

    @Test
    fun `fixed canvas and nested canvas paint locally once and route their button`() {
        // Arrange
        var actions = 0
        scenes.currScene = Canvas(size = CanvasSize.Fixed(40.0, 12.0), anchor = Anchor.Center) {
            Canvas(size = CanvasSize.Fixed(20.0, 6.0), anchor = Anchor.BottomRight) {
                Button("go", place = Place.At(Anchor.Center)) { actions++ }
            }
        }

        // Act
        manager.updateFrame()
        val bounds = backend.draws.single().second
        val handled = getManager<InputFocus>().route(
            MouseButtonEvent(
                Vector2((bounds.x + 1).toFloat(), bounds.y.toFloat()),
                InputBind.LEFT_MOUSE.name,
                InputState.JustPressed
            )
        )

        // Assert
        assertTrue(handled)
        assertEquals(1, actions)
        assertEquals(UiRect(49.0, 14.5, 2.0, 1.0), bounds)
    }

    @Test
    fun `weighted sizing redistributes capped shares and child cross alignment overrides container`() {
        // Arrange
        lateinit var first: UiNode<*>
        lateinit var second: UiNode<*>
        scenes.currScene = Canvas {
            Row(size = Size(UiLength.Fixed(30.0), UiLength.Fixed(8.0))) {
                first = Text("a", size = Size(width = UiLength.Weight(1.0))).also {
                    it.style = it.style.copy(maxWidth = 5.0)
                }
                second = Text("b", place = Place.InFlow(CrossAlign.End), size = Size(width = UiLength.Weight(2.0)))
            }
        }

        // Act
        manager.updateFrame()

        // Assert
        assertEquals(5.0, first.bounds.width)
        assertEquals(UiRect(5.0, 7.0, 25.0, 1.0), second.bounds)
    }

    @Test
    fun `arrangement distributes space after minimum gaps`() {
        // Arrange
        scenes.currScene = Canvas {
            Row(size = Size(width = UiLength.Fixed(20.0)), arrange = Arrange.SpaceBetween, gap = 2.0) {
                Text("aa")
                Text("bb")
            }
        }

        // Act
        manager.updateFrame()

        // Assert
        assertEquals(listOf(0.0, 18.0), backend.draws.map { it.second.x })
    }

    @Test
    fun `flow wraps while grid assigns equal width cells`() {
        // Arrange
        lateinit var flow: UiNode<*>
        scenes.currScene = Canvas {
            flow = FlowRow(size = Size(width = UiLength.Fixed(7.0)), gap = 1.0, lineGap = 2.0) {
                Text("aaa")
                Text("bbb")
                Text("ccc")
            }
        }

        // Act / Assert
        manager.updateFrame()
        assertEquals(listOf(0.0, 0.0, 3.0), backend.draws.map { it.second.y })
        assertEquals(4.0, flow.bounds.height)
        scenes.currScene = Canvas {
            Grid(2, UiStyle(width = UiLength.Fixed(9.0)), gap = 1.0) {
                Text("a")
                Text("b")
                Text("c")
            }
        }
        manager.updateFrame()
        assertEquals(listOf(4.0, 4.0, 4.0), backend.draws.map { it.second.width })
        assertEquals(listOf(0.0, 0.0, 2.0), backend.draws.map { it.second.y })
    }

    @Test
    fun `flow placement rejects competing anchors with an actionable diagnostic`() {
        // Arrange
        scenes.currScene = Canvas { Row { Text("invalid", place = Place.At(Anchor.Center)) } }

        // Act / Assert
        val error = assertFailsWith<IllegalArgumentException> { manager.updateFrame() }
        assertTrue("Place.InFlow" in error.message.orEmpty())
    }

    private class Drawing : Node<Drawing>("Drawing") {
        override fun onDraw(context: DrawContext) {
            context.text("world")
        }
    }

    private class MeasuredPanel : UiNode<MeasuredPanel>("MeasuredPanel", UiStyle(place = Place.At(Anchor.Center))) {
        var contentWidth = 4.0
            set(value) {
                field = value
                invalidateMeasurement()
            }
        override fun measure(backend: UiBackend, available: UiSize) = UiSize(contentWidth, 1.0)
        override fun onSetup() {
            ui { Text("x") }
        }
    }

    @Test
    fun `custom UiNode measurement invalidation repaints retained children without a drawing callback`() {
        // Arrange
        lateinit var panel: MeasuredPanel
        scenes.currScene = Canvas { panel = node("panel") { MeasuredPanel() } }
        manager.updateFrame()
        val frames = backend.frames

        // Act
        panel.contentWidth = 8.0
        manager.updateFrame()

        // Assert
        assertEquals(UiRect(36.0, 11.5, 8.0, 1.0), panel.bounds)
        assertEquals(frames + 1, backend.frames)
        assertEquals(36.0, backend.draws.single().second.x)
        manager.updateFrame()
        assertEquals(frames + 1, backend.frames)
    }

    private class MeasuredDrawing : UiNode<MeasuredDrawing>("Measured", UiStyle(place = Place.At(Anchor.Center))) {
        var caption = "tile"
            set(value) {
                field = value
                invalidateMeasurement()
            }
        override fun measure(backend: UiBackend, available: UiSize) =
            backend.measureText(caption, available.width, false)
        override fun onDraw(context: DrawContext) {
            context.text(caption)
        }
    }

    @Test
    fun `custom rectangular nodes use backend measurement and ordinary drawing callbacks`() {
        // Arrange
        lateinit var drawing: MeasuredDrawing
        scenes.currScene = Canvas { drawing = node("measured") { MeasuredDrawing() } }

        // Act
        manager.updateFrame()

        // Assert
        assertEquals("tile" to UiRect(38.0, 11.5, 4.0, 1.0), backend.draws.single())
        drawing.caption = "longer"
        manager.updateFrame()
        assertEquals("longer" to UiRect(37.0, 11.5, 6.0, 1.0), backend.draws.single())
    }

    @Test
    fun `ordinary drawings receive allocated local area and canvas overlays gameplay`() {
        // Arrange
        val world = Drawing()
        val canvas = Canvas {
            Box(size = Size(UiLength.Fixed(10.0), UiLength.Fixed(4.0)), place = Place.At(Anchor.Center)) {
                node("drawing") { Drawing() }
            }
        }
        world.addChild(canvas)
        scenes.currScene = world

        // Act
        manager.updateFrame()

        // Assert
        assertEquals(2, backend.draws.size)
        assertEquals(UiRect(35.0, 10.0, 10.0, 4.0), backend.draws.last().second)
        assertFalse(getManager<InputFocus>().blocksGameplay)
    }

    @Test
    fun `stretch and insets allocate area and placement changes invalidate layout`() {
        // Arrange
        lateinit var box: UiNode<*>
        scenes.currScene = Canvas {
            box = Box(place = Place.Stretch(Insets(2.0))) { Text("inside") }
        }

        // Act / Assert
        manager.updateFrame()
        assertEquals(UiRect(2.0, 2.0, 76.0, 20.0), box.bounds)
        box.size = Size(UiLength.Fixed(10.0), UiLength.Fixed(4.0))
        box.place = Place.At(Anchor.BottomRight, inset = Insets(1.0))
        manager.updateFrame()
        assertEquals(UiRect(69.0, 19.0, 10.0, 4.0), box.bounds)
    }

    @Test
    fun `layout values reject invalid dimensions and weights`() {
        // Arrange / Act / Assert
        assertFailsWith<IllegalArgumentException> { UiLength.Weight(0.0) }
        assertFailsWith<IllegalArgumentException> { Anchor(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { Insets(-1.0) }
        assertFailsWith<IllegalArgumentException> { CanvasSize.Fixed(-1.0, 5.0) }
    }
}
