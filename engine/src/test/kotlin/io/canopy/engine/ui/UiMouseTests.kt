package io.canopy.engine.ui

import kotlin.test.*
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.input.InputFocus
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.MouseButtonEvent
import io.canopy.engine.input.events.MouseMoveEvent
import io.canopy.engine.input.events.MouseScrollEvent
import io.canopy.engine.math.Vector2
import io.canopy.engine.ui.components.Button
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class UiMouseTests {
    private lateinit var scenes: SceneManager
    private lateinit var ui: UiManager
    private val received = mutableListOf<String>()
    private val backend = object : UiBackend {
        override fun measureText(text: String, maxWidth: Double, wrap: Boolean) = UiSize(text.length.toDouble(), 1.0)
        override fun drawText(
            text: String,
            bounds: UiRect,
            clip: UiRect,
            focused: Boolean,
            wrap: Boolean,
            appearance: UiAppearance,
        ) = Unit
    }
    private class Probe(name: String, private val action: (InputEvent) -> Unit) :
        UiNode<Probe>(
            name,
            UiStyle(width = UiLength.Fixed(10.0), height = UiLength.Fixed(3.0))
        ) {
        override fun onMouseInput(event: InputEvent) = action(event)
    }
    private fun UiScope.probe(name: String, filter: UiMouseFilter = UiMouseFilter.Ignore) =
        node(name) { Probe(name) { received += name }.also { it.mouseFilter = filter } }

    @BeforeEach
    fun setup() {
        ManagersRegistry.withScope {
            scenes = SceneManager()
            ui = UiManager(backend)
            register(scenes)
            register(InputFocus())
            register(ui)
        }
        ui.onResize(80, 24)
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    private fun click(x: Float = 1f, y: Float = 1f, action: String = InputBind.LEFT_MOUSE.name): Boolean =
        manager<InputFocus>().route(MouseButtonEvent(Vector2(x, y), action, InputState.JustPressed))

    private fun move(x: Float = 1f, y: Float = 0f) =
        manager<InputFocus>().route(MouseMoveEvent(Vector2(x, y), "MOUSE_MOVE"))

    @Test
    fun `hover enters once leaves once and never changes keyboard focus or activates buttons`() {
        // Arrange
        lateinit var button: Button<*>
        var actions = 0
        var entries = 0
        var exits = 0
        val root = UiRoot {
            button = Button("action") { actions++ }
        }
        scenes.currScene = root
        val enter = button.mouseEntered.connect { entries++ }
        val exit = button.mouseExited.connect { exits++ }
        ui.updateFrame()

        // Act
        move()
        move(2f)
        ui.updateFrame()

        // Assert
        assertTrue(button.isHovered)
        assertEquals(1, entries)
        assertEquals(0, exits)
        assertEquals(true, button.appearance().underline)
        assertNull(root.focusedElement)
        assertEquals(0, actions)
        move(70f, 20f)
        assertFalse(button.isHovered)
        assertEquals(1, exits)
        enter.disconnect()
        exit.disconnect()
    }

    @Test
    fun `hover respects pass stop ignore and changes beneath a stationary pointer`() {
        // Arrange
        lateinit var back: Probe
        lateinit var front: Probe
        scenes.currScene = UiRoot {
            back = probe("back", UiMouseFilter.Stop)
            front = probe("front", UiMouseFilter.Pass)
        }
        ui.updateFrame()

        // Act and Assert
        move()
        assertTrue(front.isHovered)
        assertTrue(back.isHovered)
        front.mouseFilter = UiMouseFilter.Stop
        ui.updateFrame()
        assertTrue(front.isHovered)
        assertFalse(back.isHovered)
        front.mouseFilter = UiMouseFilter.Ignore
        ui.updateFrame()
        assertFalse(front.isHovered)
        assertTrue(back.isHovered)
        back.isVisible = false
        ui.updateFrame()
        assertFalse(back.isHovered)
        assertFalse(ui.needsMouseMovement)
        back.isVisible = true
        ui.updateFrame()
        assertTrue(back.isHovered)
        ui.onResize(0, 0)
        ui.updateFrame()
        assertFalse(back.isHovered)
    }

    @Test
    fun `exclusive editor clears hover and closing it requires fresh pointer input`() {
        // Arrange
        lateinit var button: Button<*>
        val root = UiRoot { button = Button("action") {} }
        scenes.currScene = root
        ui.updateFrame()
        move()
        root.focus(button)
        assertTrue(ui.needsMouseMovement, "Keyboard focus must not disable hover")
        val editor = manager<InputFocus>().register(priority = Int.MAX_VALUE, capturesGameplay = { true }) { true }

        // Act and Assert
        ui.updateFrame()
        assertFalse(button.isHovered)
        assertFalse(ui.needsMouseMovement)
        move()
        editor.close()
        manager<InputFocus>().beginInputFrame()
        ui.updateFrame()
        assertTrue(ui.needsMouseMovement)
        assertFalse(button.isHovered)
        move()
        assertTrue(button.isHovered)
        scenes.currScene = null
        ui.updateFrame()
        assertFalse(button.isHovered)
    }

    @Test
    fun `hover callbacks may destroy their root without retaining or painting destroyed nodes`() {
        // Arrange
        lateinit var button: Button<*>
        val root = UiRoot { button = Button("action") {} }
        scenes.currScene = root
        val listener = button.mouseEntered.connect {
            root.queueFree()
            scenes.onUpdate(0f)
        }
        ui.updateFrame()

        // Act
        move()
        ui.updateFrame()

        // Assert
        assertFalse(root.isValid)
        assertFalse(ui.needsMouseMovement)
        listener.disconnect()
    }

    @Test
    fun `hover follows root layers clipping and disabled button style`() {
        // Arrange
        lateinit var back: Button<*>
        lateinit var front: Button<*>
        val world = EmptyNode("world")
        val lower = UiRoot("lower") { back = Button("back") {} }
        val upper = UiRoot("upper") { front = Button("front") {} }.also { it.layer = UiLayer.Overlay }
        world.addChild(lower)
        world.addChild(upper)
        scenes.currScene = world
        ui.updateFrame()
        front.enabled = false

        // Act and Assert
        move()
        assertTrue(front.isHovered)
        assertFalse(back.isHovered)
        assertEquals(true, front.appearance().dim)
        assertNull(front.appearance().underline)
        upper.style = upper.style.copy(width = UiLength.Fixed(0.0))
        ui.updateFrame()
        assertFalse(front.isHovered)
        assertTrue(back.isHovered)
    }

    @Test
    fun `ordinary elements ignore pointers and buttons accept only primary presses`() {
        var actions = 0
        lateinit var button: Button<*>
        scenes.currScene = UiRoot {
            button = Button("action") { actions++ }.also {
                it.style = UiStyle(width = UiLength.Fixed(10.0), height = UiLength.Fixed(3.0))
            }
            probe("display")
        }
        ui.updateFrame()
        assertTrue(click())
        assertEquals(1, actions)
        assertTrue(click(action = InputBind.RIGHT_MOUSE.name))
        assertEquals(1, actions)
        button.enabled = false
        assertTrue(click())
        assertEquals(1, actions)
        ui.updateFrame()
        button.mouseFilter = UiMouseFilter.Ignore
        assertFalse(click())
        assertTrue(received.isEmpty())
    }

    @Test
    fun `pass visits lower elements and stop or explicit consume prevents click through`() {
        lateinit var front: Probe
        scenes.currScene = UiRoot {
            probe("back", UiMouseFilter.Stop)
            front = probe("front", UiMouseFilter.Pass)
        }
        ui.updateFrame()
        assertTrue(click())
        assertEquals(listOf("front", "back"), received)
        received.clear()
        front.mouseFilter = UiMouseFilter.Stop
        assertTrue(click())
        assertEquals(listOf("front"), received)
        scenes.currScene = UiRoot {
            probe("back", UiMouseFilter.Stop)
            node("consuming") {
                Probe("consuming") {
                    received += "consuming"
                    it.consume()
                }.also {
                    it.mouseFilter = UiMouseFilter.Pass
                }
            }
        }
        ui.updateFrame()
        received.clear()
        assertTrue(click())
        assertEquals(listOf("consuming"), received)
    }

    @Test
    fun `mouse order follows live layer z index and registration painter order`() {
        val world = EmptyNode("world")
        val first = UiRoot("first-root") { probe("first", UiMouseFilter.Stop) }
        val second = UiRoot("second-root") { probe("second", UiMouseFilter.Stop) }
        world.addChild(first)
        world.addChild(second)
        scenes.currScene = world
        ui.updateFrame()
        assertTrue(click())
        assertEquals(listOf("second"), received)
        first.zIndex = 100
        received.clear()
        assertTrue(click())
        assertEquals(listOf("first"), received)
        second.layer = UiLayer.Overlay
        received.clear()
        assertTrue(click())
        assertEquals(listOf("second"), received)
    }

    @Test
    fun `clipped hidden and detached elements cannot receive pointer events`() {
        lateinit var child: Probe
        val root = UiRoot { child = probe("child", UiMouseFilter.Stop) }
        root.style = root.style.copy(width = UiLength.Fixed(5.0), height = UiLength.Fixed(2.0))
        scenes.currScene = root
        ui.updateFrame()
        assertFalse(click(6f, 1f))
        assertFalse(click(1f, 2f))
        assertTrue(click())
        child.isVisible = false
        assertFalse(click())
        child.isVisible = true
        scenes.currScene = null
        assertFalse(click())
        assertEquals(listOf("child"), received)
    }

    @Test
    fun `ordinary grouping nodes are transparent to hit testing and wheel routing`() {
        lateinit var target: Probe
        scenes.currScene = UiRoot {
            node("group") {
                EmptyNode("group") {
                    target = Probe("wheel") { received += "wheel" }.also { it.mouseFilter = UiMouseFilter.Stop }
                }
            }
        }
        ui.updateFrame()
        assertTrue(manager<InputFocus>().route(MouseScrollEvent(Vector2(1f, 1f), vertical = 1)))
        assertEquals(listOf("wheel"), received)
    }

    @Test
    fun `root border is clickable and handlers may remove their entire root`() {
        val root = UiRoot {}
        root.style = root.style.copy(border = UiBorder.Square)
        root.mouseFilter = UiMouseFilter.Stop
        scenes.currScene = root
        ui.updateFrame()
        assertTrue(click(0f, 0f))
        scenes.currScene = UiRoot {
            probe("back", UiMouseFilter.Stop)
            node("remove") {
                Probe("remove") { scenes.currScene = null }.also { it.mouseFilter = UiMouseFilter.Pass }
            }
        }
        ui.updateFrame()
        assertFalse(click())
        assertTrue(received.isEmpty())
    }
}
