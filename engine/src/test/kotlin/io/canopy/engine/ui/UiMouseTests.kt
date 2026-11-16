package io.canopy.engine.ui

import kotlin.test.*
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.getManager
import io.canopy.engine.core.nodes.Node2D
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.input.InputFocus
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.input.events.MouseButtonEvent
import io.canopy.engine.input.events.MouseMoveEvent
import io.canopy.engine.input.events.MouseScrollEvent
import io.canopy.engine.math.Vector2
import io.canopy.engine.ui.components.Box as BoxNode
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
        getManager<InputFocus>().route(MouseButtonEvent(Vector2(x, y), action, InputState.JustPressed))

    private fun move(x: Float = 1f, y: Float = 0f) =
        getManager<InputFocus>().route(MouseMoveEvent(Vector2(x, y), "MOUSE_MOVE"))

    @Test
    fun `direct custom UI routes input before rendering without calling its parent enter hook`() {
        class DirectProbe(style: UiStyle) : UiNode<DirectProbe>("Direct", style) {
            var clicks by nodeProperty(0)
            override fun onEnterTree() = Unit
            override fun onMouseInput(event: InputEvent) {
                clicks++
                event.consume()
            }
        }
        val direct = DirectProbe(UiStyle(width = UiLength.Fixed(10.0), height = UiLength.Fixed(3.0)))
        direct.mouseFilter = UiMouseFilter.Stop
        scenes.currScene = EmptyNode("World").also { it.addChild(direct) }

        val handled = click()

        assertTrue(handled)
        assertEquals(1, direct.clicks)
    }

    @Test
    fun `Node2D parent transform does not move viewport UI or its pointer hit`() {
        class MovingGroup : Node2D<MovingGroup>("MovingGroup")
        var clicks = 0
        val button = Button("viewport") { clicks++ }
        button.style = UiStyle(width = UiLength.Fixed(10.0), height = UiLength.Fixed(3.0))
        val group = MovingGroup().also {
            it.position = Vector2(40f, 20f)
            it.scale = Vector2(2f, 2f)
            it.addChild(button)
        }
        scenes.currScene = EmptyNode("World").also { it.addChild(group) }
        ui.updateFrame()

        assertEquals(UiRect(0.0, 0.0, 10.0, 3.0), button.bounds)
        assertTrue(click())
        assertEquals(1, clicks)
        click(41f, 21f)
        assertEquals(1, clicks)
    }

    @Test
    fun `nested canvas under direct UI paints and routes through its containing presentation once`() {
        var clicks = 0
        lateinit var button: Button<*>
        var paints = 0
        var focusedPaints = 0
        val recordingBackend = object : UiBackend by backend {
            override fun drawButton(
                text: String,
                bounds: UiRect,
                clip: UiRect,
                focused: Boolean,
                enabled: Boolean,
                wrap: Boolean,
                appearance: UiAppearance,
                border: UiBorder,
            ) {
                paints++
                if (focused) focusedPaints++
            }
        }
        ui.backend = recordingBackend
        scenes.currScene = EmptyNode("World") {
            BoxNode(content = {
                Canvas(content = {
                    button = Button("nested") { clicks++ }
                })
            })
        }

        ui.updateFrame()
        val handled = click(y = 0.5f)
        ui.updateFrame()

        assertEquals(2, paints)
        assertEquals(1, focusedPaints)
        assertTrue(handled)
        assertEquals(1, clicks)
        assertTrue(button.isHovered)
    }

    @Test
    fun `custom root registers and releases input without parent lifecycle hook calls`() {
        lateinit var button: Button<*>
        class HookRoot : UiRoot<HookRoot>(content = { button = Button("action") {} }) {
            override fun onEnterTree() = Unit
            override fun onExitTree() = Unit
        }
        val root = HookRoot()
        scenes.currScene = root
        assertTrue(click(y = 0.5f))
        assertSame(button, root.focusedElement)
        assertTrue(getManager<InputFocus>().blocksGameplay)

        scenes.currScene = null
        assertFalse(getManager<InputFocus>().blocksGameplay)
        scenes.currScene = root
        assertTrue(click(y = 0.5f))
        assertSame(button, root.focusedElement)
    }

    @Test
    fun `implicit focus clears when a button moves to another presentation`() {
        lateinit var firstGroup: EmptyNode<*>
        lateinit var secondGroup: EmptyNode<*>
        val world = EmptyNode("World") {
            BoxNode(name = "FirstBox", content = { firstGroup = node("FirstGroup") { EmptyNode("FirstGroup") } })
            BoxNode(name = "SecondBox", content = { secondGroup = node("SecondGroup") { EmptyNode("SecondGroup") } })
        }
        scenes.currScene = world
        var clicks = 0
        val button = Button("move") { clicks++ }
        firstGroup.addChild(button)
        ui.updateFrame()
        assertTrue(click(y = 0.5f))
        assertEquals(1, clicks)
        assertTrue(getManager<InputFocus>().blocksGameplay)

        firstGroup.reparent(button, secondGroup)
        getManager<InputFocus>().beginInputFrame()
        assertFalse(getManager<InputFocus>().blocksGameplay)
        assertFalse(getManager<InputFocus>().route(KeyInputEvent(Key.SPACE, state = InputState.JustPressed)))
        assertEquals(1, clicks)
        ui.updateFrame()
        assertTrue(click(y = 0.5f))
        assertEquals(2, clicks)
    }

    @Test
    fun `pointer routing stops when a callback moves its presenter beneath another UI node`() {
        val scene = EmptyNode("World")
        lateinit var destination: EmptyNode<*>
        val host = BoxNode(name = "Host", content = {
            destination = node("Destination") { EmptyNode("Destination") }
        })
        lateinit var presenter: BoxNode<*>
        presenter = BoxNode(name = "Presenter", content = {
            probe("lower", UiMouseFilter.Pass)
            node("upper") {
                Probe("upper") {
                    received += "upper"
                    scene.reparent(presenter, destination)
                }.also { it.mouseFilter = UiMouseFilter.Pass }
            }
        })
        scene.addChild(host)
        scene.addChild(presenter)
        scenes.currScene = scene
        ui.updateFrame()
        val position = Vector2(1f, 1f)
        val event = MouseButtonEvent(position, InputBind.LEFT_MOUSE.name, InputState.JustPressed)

        val handled = UiTreeInput.mouseInput(presenter, UiRect(0.0, 0.0, 80.0, 24.0), event, position)

        assertFalse(handled)
        assertEquals(listOf("upper"), received)
    }

    @Test
    fun `rootless button releases focus when hidden or detached`() {
        val world = EmptyNode("World")
        var clicks = 0
        val button = Button("direct") { clicks++ }
        world.addChild(button)
        scenes.currScene = world
        ui.updateFrame()
        val focus = getManager<InputFocus>()
        assertTrue(click(y = 0.5f))
        assertTrue(focus.blocksGameplay)

        button.hide()
        focus.beginInputFrame()
        assertFalse(focus.blocksGameplay)
        button.show()
        focus.beginInputFrame()
        assertFalse(focus.route(KeyInputEvent(Key.SPACE, state = InputState.JustPressed)))
        assertEquals(1, clicks)

        assertTrue(click(y = 0.5f))
        world.removeChild(button)
        focus.beginInputFrame()
        assertFalse(focus.blocksGameplay)
        assertFalse(focus.route(KeyInputEvent(Key.SPACE, state = InputState.JustPressed)))
        assertEquals(2, clicks)
    }

    @Test
    fun `closed exclusive prompt still blocks rootless UI input for its captured frame`() {
        var clicks = 0
        scenes.currScene = EmptyNode("World") { Button("direct") { clicks++ } }
        ui.updateFrame()
        val focus = getManager<InputFocus>()
        val prompt = focus.register(priority = Int.MAX_VALUE, capturesGameplay = { true }) { true }
        focus.beginInputFrame()
        prompt.close()

        assertTrue(focus.route(KeyInputEvent(Key.DOWN, state = InputState.JustPressed)))
        assertTrue(click())
        assertEquals(0, clicks)

        focus.beginInputFrame()
        assertTrue(click(y = 0.5f))
        assertEquals(1, clicks)
    }

    @Test
    fun `external keyboard route precedes implicit focus but follows higher explicit focus`() {
        var implicitActions = 0
        scenes.currScene = EmptyNode("World") { Button("direct") { implicitActions++ } }
        ui.updateFrame()
        assertTrue(click(y = 0.5f))
        assertEquals(1, implicitActions)
        var externalActions = 0
        val focus = getManager<InputFocus>()
        val external = focus.register(priority = 0) { event ->
            if (event is KeyInputEvent && event.key == Key.SPACE) {
                externalActions++
                true
            } else {
                false
            }
        }
        focus.beginInputFrame()
        assertTrue(focus.route(KeyInputEvent(Key.SPACE, state = InputState.JustPressed)))
        assertEquals(1, implicitActions)
        assertEquals(1, externalActions)

        lateinit var button: Button<*>
        val root = UiRoot(content = { button = Button("explicit") { implicitActions++ } })
        root.zIndex = 1
        scenes.currScene = root
        root.focus(button)
        focus.beginInputFrame()
        assertTrue(focus.route(KeyInputEvent(Key.SPACE, state = InputState.JustPressed)))
        assertEquals(2, implicitActions)
        assertEquals(1, externalActions)
        external.close()
    }

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
        val editor = getManager<InputFocus>().register(priority = Int.MAX_VALUE, capturesGameplay = { true }) { true }

        // Act and Assert
        ui.updateFrame()
        assertFalse(button.isHovered)
        assertFalse(ui.needsMouseMovement)
        move()
        editor.close()
        getManager<InputFocus>().beginInputFrame()
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
        assertTrue(getManager<InputFocus>().route(MouseScrollEvent(Vector2(1f, 1f), vertical = 1)))
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
