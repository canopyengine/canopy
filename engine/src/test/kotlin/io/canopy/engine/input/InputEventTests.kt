package io.canopy.engine.input

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.canopy.engine.math.Vector2

class InputEventTests {

    @Test
    fun `consume marks event as handled`() {
        val event = ButtonInputEvent("jump", InputState.JustPressed)

        assertFalse(event.isHandled)

        event.consume()

        assertTrue(event.isHandled)
    }

    @Test
    fun `action helpers match pressed and released states`() {
        val pressed = ButtonInputEvent("jump", InputState.Pressed)
        val justPressed = ButtonInputEvent("jump", InputState.JustPressed)
        val released = ButtonInputEvent("jump", InputState.Released)
        val justReleased = ButtonInputEvent("jump", InputState.JustReleased)

        assertTrue(pressed.isActionPressed("jump"))
        assertTrue(justPressed.isActionPressed("jump"))
        assertTrue(justPressed.isActionJustPressed("jump"))
        assertTrue(released.isActionReleased("jump"))
        assertTrue(justReleased.isActionReleased("jump"))
        assertTrue(justReleased.isActionJustReleased("jump"))
        assertFalse(pressed.isActionPressed("other"))
    }

    @Test
    fun `any action helpers reflect exact state`() {
        assertTrue(ButtonInputEvent("jump", InputState.Pressed).isAnyActionPressed())
        assertTrue(ButtonInputEvent("jump", InputState.JustPressed).isAnyActionJustPressed())
        assertTrue(ButtonInputEvent("jump", InputState.Released).isAnyActionReleased())
        assertTrue(ButtonInputEvent("jump", InputState.JustReleased).isAnyActionJustReleased())
        assertFalse(MouseMoveEvent(Vector2(1f, 2f), "move").isAnyActionPressed())
    }
}
