package io.canopy.engine.ui

import kotlin.test.*
import io.canopy.engine.core.exceptions.NodeCallbackException
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.graphics.DrawContext
import io.canopy.engine.input.InputFocus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class ScopedDrawingTests {
    private class DrawingNode(name: String, draw: (DrawContext) -> Unit, block: DrawingNode.() -> Unit = {}) :
        Node<DrawingNode>(name, block = block) {
        private val drawing by nodeProperty(draw)
        override fun onDraw(context: DrawContext) = drawing(context)
    }

    private class Backend : UiBackend {
        data class Draw(val text: String, val bounds: UiRect, val clip: UiRect)
        val draws = mutableListOf<Draw>()
        var frames = 0
        var commits = 0
        var aborts = 0
        override fun measureText(text: String, maxWidth: Double, wrap: Boolean) =
            UiSize(text.length.toDouble().coerceAtMost(maxWidth), 1.0)
        override fun begin(viewport: UiSize) {
            frames++
            draws.clear()
        }
        override fun drawText(text: String, bounds: UiRect, clip: UiRect, focused: Boolean, wrap: Boolean) {
            draws += Draw(text, bounds, clip)
        }
        override fun end() {
            commits++
        }
        override fun abort() {
            aborts++
            draws.clear()
        }
    }

    private lateinit var scenes: SceneManager
    private lateinit var ui: UiManager
    private lateinit var backend: Backend

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
    fun `drawing declaration rejects factories creating untracked siblings and rolls them back`() {
        // Arrange
        lateinit var first: DrawingNode
        lateinit var second: DrawingNode
        val root = UiRoot {
            node("invalid") {
                first = DrawingNode("first", {})
                second = DrawingNode("second", {})
                second
            }
        }

        // Act / Assert
        assertFailsWith<NodeCallbackException> { scenes.currScene = root }
        assertTrue(first.isFreed)
        assertTrue(second.isFreed)
        assertFalse(scenes.hasDrawables)
    }

    @Test
    fun `ordinary subtree inherits padded content area and paints in declaration order`() {
        // Arrange
        val sizes = mutableListOf<UiSize>()
        scenes.currScene = UiRoot {
            Box(UiStyle(width = UiLength.Fixed(10.0), height = UiLength.Fixed(5.0), padding = 1.0)) {
                node("map") {
                    DrawingNode("map", { context ->
                        sizes += context.size
                        context.text("outside", x = -2.0, y = -2.0, width = 20.0, height = 10.0)
                    }) {
                        DrawingNode("agent", { context ->
                            sizes += context.size
                            context.text("agent", x = 1.0, y = 1.0)
                        })
                    }
                }
                Text("overlay")
            }
        }

        // Act
        ui.updateFrame()

        // Assert
        assertEquals(listOf(UiSize(8.0, 3.0), UiSize(8.0, 3.0)), sizes)
        assertEquals(listOf("outside", "agent", "overlay"), backend.draws.map { it.text })
        assertEquals(UiRect(-1.0, -1.0, 20.0, 10.0), backend.draws[0].bounds)
        assertEquals(UiRect(1.0, 1.0, 8.0, 3.0), backend.draws[0].clip)
        assertEquals(UiRect(2.0, 2.0, 7.0, 2.0), backend.draws[1].bounds)
    }

    @Test
    fun `disabling container clipping allows overflow within the host viewport`() {
        // Arrange
        scenes.currScene = UiRoot {
            Box(UiStyle(width = UiLength.Fixed(4.0), height = UiLength.Fixed(2.0), clip = false)) {
                node("map") { DrawingNode("map", { it.text("overflow", width = 8.0, height = 4.0) }) }
            }
        }

        // Act
        ui.updateFrame()

        // Assert
        assertEquals(UiRect(0.0, 0.0, 8.0, 4.0), backend.draws.single().clip)
    }

    @Test
    fun `drawing stays live while paused and hidden or detached content clears`() {
        // Arrange
        var draws = 0
        val root = DrawingNode("world", { it.text("frame ${++draws}") })
        scenes.currScene = root
        scenes.pauseState = { true }

        // Act / Assert
        ui.updateFrame()
        ui.updateFrame()
        assertEquals(2, draws)
        assertEquals("frame 2", backend.draws.single().text)
        root.hide()
        ui.updateFrame()
        assertTrue(backend.draws.isEmpty())
        root.show()
        ui.updateFrame()
        assertEquals(3, draws)
        scenes.currScene = null
        ui.updateFrame()
        assertTrue(backend.draws.isEmpty())
        val frames = backend.frames
        ui.updateFrame()
        assertEquals(frames, backend.frames)
    }

    @Test
    fun `retained drawing nodes initialize once across keyed reorder and free on omission`() {
        // Arrange
        val ids = signal(owner = null, listOf("a", "b"))
        val nodes = mutableMapOf<String, DrawingNode>()
        var creations = 0
        scenes.currScene = UiRoot {
            Box {
                structure("maps") {
                    ids().forEach { id ->
                        key(id) {
                            node("map") {
                                creations++
                                DrawingNode(id, { it.text(id) }).also { nodes[id] = it }
                            }
                        }
                    }
                }
            }
        }
        ui.updateFrame()
        val first = nodes.getValue("a")

        // Act / Assert
        ids.update { listOf("b", "a") }
        ui.updateFrame()
        assertEquals(2, creations)
        assertSame(first, nodes["a"])
        assertEquals(listOf("b", "a"), backend.draws.map { it.text })
        ids.update { listOf("b") }
        ui.updateFrame()
        scenes.onUpdate(0f)
        assertTrue(first.isFreed)
        assertEquals(listOf("b"), backend.draws.map { it.text })
        ids.dispose()
    }

    @Test
    fun `drawing failure aborts frame expires context and can retry`() {
        // Arrange
        val failure = IllegalStateException("draw")
        var fail = true
        var saved: DrawContext? = null
        scenes.currScene = DrawingNode("world", {
            saved = it
            it.text("pending")
            if (fail) throw failure
        })

        // Act / Assert
        val error = assertFailsWith<NodeCallbackException> { ui.updateFrame() }
        assertSame(failure, error.cause)
        assertEquals(0, backend.commits)
        assertEquals(1, backend.aborts)
        assertTrue(backend.draws.isEmpty())
        assertFailsWith<IllegalStateException> { saved!!.text("late") }
        fail = false
        ui.updateFrame()
        assertEquals(1, backend.commits)
        assertEquals("pending", backend.draws.single().text)
        assertFailsWith<IllegalStateException> { saved!!.measureText("late") }
    }
}
