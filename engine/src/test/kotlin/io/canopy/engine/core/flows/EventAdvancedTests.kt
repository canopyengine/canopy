package io.canopy.engine.core.flows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.canopy.engine.core.flows.events.EventWeakListeners
import io.canopy.engine.core.flows.events.event

class EventAdvancedTests {

    @Test
    fun `event factories support all arities`() {
        val noArg = event()
        val oneArg = event<Int>()
        val twoArg = event<Int, String>()
        var calls = ""

        noArg.connect { calls += "a" }
        oneArg.connect { calls += it.toString() }
        twoArg.connect { a, b -> calls += "$a$b" }

        noArg.emit()
        oneArg.emit(2)
        twoArg.emit(3, "b")

        assertEquals("a23b", calls)
        assertFalse(noArg.isEmpty())
        noArg.clear()
        assertTrue(noArg.isEmpty())
    }

    @Test
    fun `weak listeners add remove and clear strong references`() {
        val listeners = EventWeakListeners<() -> Unit>("test")
        var calls = 0
        val callback: () -> Unit = {
            calls++
            Unit
        }

        listeners.add(callback)
        assertEquals(1, listeners.size())

        listeners.forEach { it() }
        assertEquals(1, calls)

        listeners.remove(callback)
        assertEquals(0, listeners.size())

        listeners.add(callback)
        listeners.clear()
        assertEquals(0, listeners.size())
    }
}
