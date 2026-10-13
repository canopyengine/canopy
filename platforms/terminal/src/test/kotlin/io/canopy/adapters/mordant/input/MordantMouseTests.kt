package io.canopy.adapters.mordant.input

import kotlin.test.*
import com.github.ajalt.mordant.input.MouseEvent
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.input.InputFocus
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.MouseButtonEvent
import io.canopy.engine.input.events.MouseScrollEvent
import io.canopy.engine.input.events.TextInputEvent
import io.canopy.engine.math.Vector2
import org.junit.jupiter.api.AfterEach

class MordantMouseTests {
    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `mouse snapshots produce one press and release with coordinates and modifiers but no text`() {
        val input = MordantInputManager()
        input.enqueueMordantMouseEvent(MouseEvent(3, 4, left = true, ctrl = true))
        input.enqueueMordantMouseEvent(MouseEvent(3, 4, left = true, ctrl = true))
        input.enqueueMordantMouseEvent(MouseEvent(4, 5))
        input.processEvents()
        val events = input.eventsThisFrame.filterIsInstance<MouseButtonEvent>()
        assertEquals(listOf(InputState.JustPressed, InputState.JustReleased), events.map { it.state })
        assertEquals(InputBind.LEFT_MOUSE.name, events.first().action)
        assertEquals(Vector2(3f, 4f), events.first().screenPos)
        assertTrue(events.first().ctrl)
        assertTrue(input.eventsThisFrame.none { it is TextInputEvent })
    }

    @Test
    fun `wheels do not release held buttons and mouse mappings reflect release`() {
        val input = MordantInputManager()
        input.mapActions("fire" to listOf(InputBind.LEFT_MOUSE))
        input.enqueueMordantMouseEvent(MouseEvent(1, 2, left = true))
        input.processEvents()
        assertTrue(input.isActionJustPressed("fire"))
        input.enqueueMordantMouseEvent(MouseEvent(1, 2, wheelDown = true, wheelRight = true, shift = true))
        input.processEvents()
        val scroll = assertIs<MouseScrollEvent>(input.eventsThisFrame.single())
        assertEquals(1, scroll.vertical)
        assertEquals(1, scroll.horizontal)
        assertTrue(scroll.shift)
        assertTrue(input.isPressed(InputBind.LEFT_MOUSE))
        input.enqueueMordantMouseEvent(MouseEvent(1, 2))
        input.processEvents()
        assertTrue(input.isActionJustReleased("fire"))
    }

    @Test
    fun `a consumed mouse press cannot become gameplay after its UI disappears until released`() {
        val input = MordantInputManager()
        val focus = InputFocus()
        ManagersRegistry.withScope {
            register(focus)
            register(input)
        }
        input.mapActions("fire" to listOf(InputBind.LEFT_MOUSE))
        val lease = focus.register { it is MouseButtonEvent }
        input.enqueueMordantMouseEvent(MouseEvent(1, 2, left = true))
        input.processEvents()
        assertFalse(input.isPressed(InputBind.LEFT_MOUSE))
        lease.close()
        input.processEvents()
        assertFalse(input.isActionPressed("fire"))
        input.enqueueMordantMouseEvent(MouseEvent(1, 2))
        input.processEvents()
        assertTrue(input.eventsThisFrame.isEmpty(), "UI-owned release leaked to gameplay")
        input.enqueueMordantMouseEvent(MouseEvent(1, 2, left = true))
        input.processEvents()
        assertTrue(input.isActionJustPressed("fire"))
    }

    @Test
    fun `backend shutdown clears held snapshots and capture for reuse`() {
        val input = MordantInputManager()
        input.enqueueMordantMouseEvent(MouseEvent(1, 2, right = true))
        input.processEvents()
        assertTrue(input.isPressed(InputBind.RIGHT_MOUSE))
        input.onExit()
        assertFalse(input.isPressed(InputBind.RIGHT_MOUSE))
        input.enqueueMordantMouseEvent(MouseEvent(1, 2, right = true))
        input.processEvents()
        assertEquals(InputState.JustPressed, input.eventsThisFrame.single().state)
    }
}
