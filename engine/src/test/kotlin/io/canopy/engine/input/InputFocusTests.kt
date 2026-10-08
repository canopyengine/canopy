package io.canopy.engine.input

import kotlin.test.*
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.input.events.TextInputEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class InputFocusTests {
    private class Input : InputManager() {
        override fun pollPressed(bind: InputBind) = true
    }

    private lateinit var focus: InputFocus
    private lateinit var scenes: SceneManager
    private lateinit var input: Input

    @BeforeEach
    fun setup() {
        focus = InputFocus()
        scenes = SceneManager()
        input = Input()
        ManagersRegistry.withScope {
            register(focus)
            register(scenes)
            register(input)
        }
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `priority routing and consumption stop later routes and gameplay events`() {
        // Arrange
        val calls = mutableListOf<String>()
        focus.register {
            calls += "later"
            false
        }
        val lease = focus.register(priority = 10) {
            calls += "first"
            it.consume()
            false
        }
        input.enqueue(TextInputEvent("hello"))
        // Act
        input.processEvents()
        // Assert
        assertEquals(listOf("first"), calls)
        assertTrue(input.eventsThisFrame.isEmpty())
        lease.close()
        lease.close()
        input.enqueue(TextInputEvent("world"))
        input.processEvents()
        assertEquals(listOf("first", "later"), calls)
        assertEquals(1, input.eventsThisFrame.size)
    }

    @Test
    fun `exclusive focus keeps entire frame captured after its handler releases lease`() {
        // Arrange
        lateinit var lease: AutoCloseable
        lease = focus.register(capturesGameplay = { true }) {
            lease.close()
            true
        }
        input.enqueue(TextInputEvent("close"))
        input.enqueue(TextInputEvent("must not leak"))
        // Act
        input.processEvents()
        // Assert
        assertTrue(focus.blocksGameplay)
        assertTrue(input.eventsThisFrame.isEmpty())
        input.processEvents()
        assertFalse(focus.blocksGameplay)
    }

    @Test
    fun `owner visibility disables focus and reusable removal releases route permanently`() {
        // Arrange
        val parent = EmptyNode("root") { EmptyNode("child") }
        scenes.currScene = parent
        val child = parent.getNode<EmptyNode>("child")
        var routed = 0
        focus.register(owner = child, capturesGameplay = { true }) {
            routed++
            true
        }
        assertTrue(focus.blocksGameplay)
        // Act
        parent.hide()
        input.enqueue(TextInputEvent("gameplay"))
        input.processEvents()
        // Assert
        assertFalse(focus.blocksGameplay)
        assertEquals(0, routed)
        assertEquals(1, input.eventsThisFrame.size)
        parent.show()
        input.enqueue(TextInputEvent("editor"))
        input.processEvents()
        assertEquals(1, routed)
        parent.removeChild(child)
        parent.addChild(child)
        input.enqueue(TextInputEvent("gameplay again"))
        input.processEvents()
        assertFalse(focus.blocksGameplay)
        assertEquals(1, routed)
    }

    @Test
    fun `capture suppresses polling and actions immediately while Ctrl C remains raw host input`() {
        // Arrange
        input.mapActions("move" to listOf(InputBind.W))
        input.processEvents()
        assertTrue(input.isPressed(InputBind.W))
        assertTrue(input.isActionJustPressed("move"))
        var routed = 0
        val lease = focus.register(capturesGameplay = { true }) {
            routed++
            true
        }
        // Act
        assertFalse(input.isPressed(InputBind.W))
        assertEquals(InputState.Released, input.getActionState("move"))
        input.enqueue(KeyInputEvent(Key.C_KEY, ctrl = true, state = InputState.JustPressed))
        input.processEvents()
        // Assert
        assertEquals(0, routed)
        assertEquals(1, input.eventsThisFrame.size)
        assertTrue(input.actionStates.isEmpty())
        lease.close()
        assertFalse(input.isPressed(InputBind.W))
        input.processEvents()
        assertTrue(input.isActionJustPressed("move"))
        assertTrue(input.isPressed(InputBind.W))
    }

    @Test
    fun `already consumed events bypass all routes`() {
        // Arrange
        var routed = false
        focus.register {
            routed = true
            true
        }
        input.enqueue(TextInputEvent("consumed").apply { consume() })
        // Act
        input.processEvents()
        // Assert
        assertFalse(routed)
        assertTrue(input.eventsThisFrame.isEmpty())
        assertEquals(InputState.Released, input.getActionState("unknown"))
    }
}
