package io.canopy.engine.input

import kotlin.test.*
import io.canopy.engine.input.events.ButtonInputEvent
import io.canopy.engine.input.events.InputState

class InputEventPredicateTests {
    @Test
    fun `named and any action predicates agree including transition states`() {
        // Arrange / Act / Assert
        for (state in InputState.entries) {
            val event = ButtonInputEvent("feed", state)
            assertEquals(event.isActionPressed("feed"), event.isAnyActionPressed())
            assertEquals(event.isActionReleased("feed"), event.isAnyActionReleased())
            assertEquals(state == InputState.JustPressed, event.isAnyActionJustPressed())
            assertEquals(state == InputState.JustReleased, event.isAnyActionJustReleased())
            assertFalse(event.isActionPressed("other"))
        }
        assertTrue(ButtonInputEvent("feed", InputState.JustPressed).isAnyActionPressed())
        assertTrue(ButtonInputEvent("feed", InputState.JustReleased).isAnyActionReleased())
    }
}
