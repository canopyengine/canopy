package io.canopy.engine.core.flows.events

import kotlin.test.Test
import kotlin.test.assertEquals

class EventsTests {

    @Test
    fun `emit NoArgEvent`() {
        val event = NoArgEvent()
        var count = 0
        event.connect { count++ }
        event.emit()
        assertEquals(1, count)
    }

    @Test
    fun `emit OneArgEvent`() {
        val event = OneArgEvent<String>()
        var arg = ""
        event.connect { a -> arg = a }
        event.emit("test")
        assertEquals("test", arg)
    }

    @Test
    fun `emit TwoArgsEvent`() {
        val event = TwoArgsEvent<String, Int>()
        var res = ""
        event.connect { a, b -> res = a + b }
        event.emit("test", 1)
        assertEquals("test1", res)
    }
}
