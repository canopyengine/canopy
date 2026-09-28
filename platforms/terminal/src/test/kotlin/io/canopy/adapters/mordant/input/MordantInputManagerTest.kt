package io.canopy.adapters.mordant.input

import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.binds.toKey
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.KeyInputEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MordantInputManagerTest {

    @Test
    fun `should track just-pressed keys`() {
        val mgr = MordantInputManager()
        mgr.mapActions("jump" to listOf(InputBind.SPACE))

        // Simulate key press
        val event = KeyInputEvent(key = InputBind.SPACE.toKey(), state = InputState.JustPressed)
        mgr.enqueue(event)

        mgr.processEvents()

        assertTrue(mgr.isActionJustPressed("jump"))
        assertEquals(InputState.JustPressed, mgr.getActionState("jump"))
    }

    @Test
    fun `should transition to pressed on next frame when key held`() {
        val mgr = MordantInputManager()
        mgr.mapActions("jump" to listOf(InputBind.SPACE))

        // Frame 1: JustPressed
        mgr.enqueue(KeyInputEvent(key = InputBind.SPACE.toKey(), state = InputState.JustPressed))
        mgr.processEvents()
        assertEquals(InputState.JustPressed, mgr.getActionState("jump"))

        // Frame 2: still held (simulate by sending Pressed)
        mgr.enqueue(KeyInputEvent(key = InputBind.SPACE.toKey(), state = InputState.Pressed))
        mgr.processEvents()
        assertEquals(InputState.Pressed, mgr.getActionState("jump"))
    }

    @Test
    fun `should transition to just-released when key released`() {
        val mgr = MordantInputManager()
        mgr.mapActions("jump" to listOf(InputBind.SPACE))

        // Frame 1: Pressed
        mgr.enqueue(KeyInputEvent(key = InputBind.SPACE.toKey(), state = InputState.Pressed))
        mgr.processEvents()
        assertEquals(InputState.JustPressed, mgr.getActionState("jump"))

        // Frame 2: Released
        mgr.enqueue(KeyInputEvent(key = InputBind.SPACE.toKey(), state = InputState.Released))
        mgr.processEvents()
        assertEquals(InputState.JustReleased, mgr.getActionState("jump"))
    }

    @Test
    fun `should transition to released after just-released`() {
        val mgr = MordantInputManager()
        mgr.mapActions("jump" to listOf(InputBind.SPACE))

        mgr.enqueue(KeyInputEvent(key = InputBind.SPACE.toKey(), state = InputState.Pressed))
        mgr.processEvents()

        mgr.enqueue(KeyInputEvent(key = InputBind.SPACE.toKey(), state = InputState.Released))
        mgr.processEvents()
        assertEquals(InputState.JustReleased, mgr.getActionState("jump"))

        // Frame 3: no events → should become Released
        mgr.processEvents()
        assertEquals(InputState.Released, mgr.getActionState("jump"))
    }

    @Test
    fun `should handle multiple actions independently`() {
        val mgr = MordantInputManager()
        mgr.mapActions(
            "left" to listOf(InputBind.LEFT, InputBind.A),
            "right" to listOf(InputBind.RIGHT, InputBind.D),
            "jump" to listOf(InputBind.SPACE)
        )

        // Frame 1: Press left
        mgr.enqueue(KeyInputEvent(key = InputBind.LEFT.toKey(), state = InputState.JustPressed))
        mgr.processEvents()

        assertTrue(mgr.isActionJustPressed("left"))
        assertFalse(mgr.isActionPressed("right"))
        assertFalse(mgr.isActionPressed("jump"))

        // Frame 2: Press right (left still held - simulate by sending Pressed for left)
        mgr.enqueue(KeyInputEvent(key = InputBind.LEFT.toKey(), state = InputState.Pressed))
        mgr.enqueue(KeyInputEvent(key = InputBind.RIGHT.toKey(), state = InputState.JustPressed))
        mgr.processEvents()

        assertTrue(mgr.isActionPressed("left"))
        assertTrue(mgr.isActionJustPressed("right"))
        assertFalse(mgr.isActionPressed("jump"))
    }

    @Test
    fun `should compute axis from opposing actions`() {
        val mgr = MordantInputManager()
        mgr.mapActions(
            "move_left" to listOf(InputBind.LEFT, InputBind.A),
            "move_right" to listOf(InputBind.RIGHT, InputBind.D)
        )

        // Frame 1: Nothing pressed
        mgr.processEvents()
        assertEquals(0f, mgr.getAxis("move_left", "move_right"))

        // Frame 2: Press left
        mgr.enqueue(KeyInputEvent(key = InputBind.LEFT.toKey(), state = InputState.JustPressed))
        mgr.processEvents()
        assertEquals(-1f, mgr.getAxis("move_left", "move_right"))

        // Frame 3: Press right (both pressed - send both events in same frame)
        mgr.enqueue(KeyInputEvent(key = InputBind.LEFT.toKey(), state = InputState.Pressed))
        mgr.enqueue(KeyInputEvent(key = InputBind.RIGHT.toKey(), state = InputState.JustPressed))
        mgr.processEvents()
        assertEquals(0f, mgr.getAxis("move_left", "move_right"))

        // Frame 4: Release left
        mgr.enqueue(KeyInputEvent(key = InputBind.LEFT.toKey(), state = InputState.Released))
        mgr.enqueue(KeyInputEvent(key = InputBind.RIGHT.toKey(), state = InputState.Pressed))
        mgr.processEvents()
        assertEquals(1f, mgr.getAxis("move_left", "move_right"))
    }

    @Test
    fun `should compute 2D input vector`() {
        val mgr = MordantInputManager()
        mgr.mapActions(
            "move_left" to listOf(InputBind.LEFT),
            "move_right" to listOf(InputBind.RIGHT),
            "move_up" to listOf(InputBind.UP),
            "move_down" to listOf(InputBind.DOWN)
        )

        mgr.processEvents()
        assertEquals(0f, mgr.getInputVector("move_left", "move_right", "move_up", "move_down").x)
        assertEquals(0f, mgr.getInputVector("move_left", "move_right", "move_up", "move_down").y)

        // Frame with right + up
        mgr.enqueue(KeyInputEvent(key = InputBind.RIGHT.toKey(), state = InputState.JustPressed))
        mgr.enqueue(KeyInputEvent(key = InputBind.UP.toKey(), state = InputState.JustPressed))
        mgr.processEvents()

        val vec = mgr.getInputVector("move_left", "move_right", "move_up", "move_down")
        assertEquals(1f, vec.x)
        assertEquals(-1f, vec.y) // UP is negative Y in typical screen coords
    }

    @Test
    fun `should clear pressed state each frame in processEvents`() {
        val mgr = MordantInputManager()
        mgr.mapActions("jump" to listOf(InputBind.SPACE))

        // Frame 1
        mgr.enqueue(KeyInputEvent(key = InputBind.SPACE.toKey(), state = InputState.JustPressed))
        mgr.processEvents()
        assertTrue(mgr.isActionJustPressed("jump"))

        // Frame 2 - no new events, but processEvents called again
        mgr.processEvents()
        // Since Mordant only sends press events, and we clear the frame state,
        // it should transition to released.
        assertTrue(mgr.isActionJustReleased("jump"))
    }
}