package io.canopy.engine.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.InputData
import io.canopy.engine.input.binds.Key
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.math.Vector2

class InputManagerTests {
    private class TestInputManager : InputManager() {
        val pressed = mutableSetOf<InputBind>()
        val handled = mutableListOf<InputEvent>()

        override fun pollPressed(bind: InputBind): Boolean = bind in pressed
        override fun handleEvent(event: InputEvent) {
            handled += event
        }
    }

    @Test
    fun `action state advances through press and release edges`() {
        val input = TestInputManager()
        input.mapActions("jump" to listOf(InputBind.SPACE))

        input.pressed += InputBind.SPACE
        input.processEvents()
        assertEquals(InputState.JustPressed, input.getActionState("jump"))
        assertTrue(input.isActionPressed("jump"))

        input.processEvents()
        assertEquals(InputState.Pressed, input.getActionState("jump"))

        input.pressed -= InputBind.SPACE
        input.processEvents()
        assertEquals(InputState.JustReleased, input.getActionState("jump"))
        assertTrue(input.isActionReleased("jump"))

        input.processEvents()
        assertEquals(InputState.Released, input.getActionState("jump"))
        assertFalse(input.isActionPressed("jump"))
    }

    @Test
    fun `axes and input vector follow mapped actions`() {
        val input = TestInputManager()
        input.mapActions(
            "left" to listOf(InputBind.A),
            "right" to listOf(InputBind.D),
            "up" to listOf(InputBind.W),
            "down" to listOf(InputBind.S)
        )
        input.pressed.addAll(listOf(InputBind.D, InputBind.W))
        input.processEvents()

        assertEquals(1f, input.getAxis("left", "right"))
        assertEquals(Vector2(1f, 1f), input.getInputVector("left", "right", "down", "up"))

        input.pressed += InputBind.A
        input.processEvents()
        assertEquals(0f, input.getAxis("left", "right"))
    }

    @Test
    fun `process events drains queue and exposes frame events once`() {
        val input = TestInputManager()
        val event = KeyInputEvent(Key.SPACE, state = InputState.JustPressed)
        val expectedEvents: List<InputEvent> = listOf(event)
        input.enqueue(event)

        input.processEvents()

        assertEquals(expectedEvents, input.handled)
        assertEquals(expectedEvents, input.eventsThisFrame)
        assertEquals(expectedEvents, input.consumeEventsThisFrame())
        assertTrue(input.consumeEventsThisFrame().isEmpty())

        input.processEvents()
        assertTrue(input.eventsThisFrame.isEmpty())
    }

    @Test
    fun `mappings can be extended removed and cleared`() {
        val input = TestInputManager()
        input.mapActions("move" to listOf(InputBind.A))
        input.mapActions("move" to listOf(InputBind.D), replace = false)
        input.pressed += InputBind.D
        input.processEvents()

        assertTrue(input.isActionJustPressed("move"))
        assertEquals(1f, input.getAxis("missing", "move"))
        assertTrue(input.isPressed(InputBind.D))

        input.unmapAction("move")
        assertEquals(InputState.Released, input.getActionState("move"))
        input.mapActions("move" to listOf(InputBind.D))
        input.clearMappings()
        assertEquals(InputState.Released, input.getActionState("move"))
        assertFalse(input.isActionJustReleased("move"))
    }

    @Test
    fun `input mapper serializes and restores keyboard and mouse mappings`() {
        val mapper = InputMapper()
        mapper.mapActions("select" to listOf(InputBind.ENTER, InputBind.LEFT_MOUSE))

        val data: InputData = mapper.toData()
        val restored = InputMapper()
        restored.loadData(data)

        assertEquals(mapper.actions, restored.actions)
        restored.mapActions("select" to listOf(InputBind.SPACE))
        assertEquals(mapOf("select" to listOf(InputBind.SPACE)), restored.actions)

        restored.unmapAction("select")
        restored.clearMappings()
        assertTrue(restored.actions.isEmpty())
    }
}
