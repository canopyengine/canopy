package io.canopy.adapters.mordant.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.toKey
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import io.canopy.engine.math.Vector2

class MordantInputManagerTest {
    @Test
    fun `key events advance mapped actions through press held and release states`() {
        val input = MordantInputManager()
        input.mapActions("jump" to listOf(InputBind.SPACE))

        fun frame(state: InputState): InputState {
            input.enqueue(KeyInputEvent(InputBind.SPACE.toKey(), state = state))
            input.processEvents()
            return input.getActionState("jump")
        }

        assertEquals(InputState.JustPressed, frame(InputState.JustPressed))
        assertTrue(input.isActionJustPressed("jump"))
        assertEquals(InputState.Pressed, frame(InputState.Pressed))
        assertEquals(InputState.JustReleased, frame(InputState.Released))
        input.processEvents()
        assertEquals(InputState.Released, input.getActionState("jump"))
    }

    @Test
    fun `multiple mappings remain independent when events arrive together`() {
        val input = MordantInputManager()
        input.mapActions(
            "left" to listOf(InputBind.LEFT, InputBind.A),
            "right" to listOf(InputBind.RIGHT, InputBind.D),
            "jump" to listOf(InputBind.SPACE)
        )
        input.enqueue(KeyInputEvent(InputBind.LEFT.toKey(), state = InputState.JustPressed))
        input.processEvents()
        assertTrue(input.isActionJustPressed("left"))
        assertFalse(input.isActionPressed("right"))
        assertFalse(input.isActionPressed("jump"))

        input.enqueue(KeyInputEvent(InputBind.LEFT.toKey(), state = InputState.Pressed))
        input.enqueue(KeyInputEvent(InputBind.RIGHT.toKey(), state = InputState.JustPressed))
        input.processEvents()
        assertTrue(input.isActionPressed("left"))
        assertTrue(input.isActionJustPressed("right"))
        assertFalse(input.isActionPressed("jump"))
    }

    @Test
    fun `backend events drive opposing axes and screen coordinate vectors`() {
        val input = MordantInputManager()
        input.mapActions(
            "left" to listOf(InputBind.LEFT, InputBind.A),
            "right" to listOf(InputBind.RIGHT, InputBind.D),
            "up" to listOf(InputBind.UP),
            "down" to listOf(InputBind.DOWN)
        )
        fun vector() = input.getInputVector("left", "right", "up", "down")
        fun frame(vararg binds: InputBind) {
            binds.forEach { input.enqueue(KeyInputEvent(it.toKey(), state = InputState.JustPressed)) }
            input.processEvents()
        }

        frame()
        assertEquals(0f, input.getAxis("left", "right"))
        assertEquals(Vector2(), vector())
        frame(InputBind.LEFT)
        assertEquals(-1f, input.getAxis("left", "right"))
        frame(InputBind.LEFT, InputBind.RIGHT)
        assertEquals(0f, input.getAxis("left", "right"))
        frame(InputBind.RIGHT, InputBind.UP)
        assertEquals(1f, input.getAxis("left", "right"))
        assertEquals(Vector2(1f, -1f), vector())
    }

    @Test
    fun `a key without a new terminal event releases on the next frame`() {
        val input = MordantInputManager()
        input.mapActions("jump" to listOf(InputBind.SPACE))
        input.enqueue(KeyInputEvent(InputBind.SPACE.toKey(), state = InputState.JustPressed))
        input.processEvents()
        assertTrue(input.isActionJustPressed("jump"))

        input.processEvents()

        assertTrue(input.isActionJustReleased("jump"))
    }
}
