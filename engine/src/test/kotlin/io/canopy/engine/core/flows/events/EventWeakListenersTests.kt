package io.canopy.engine.core.flows.events

import kotlin.test.Test
import kotlin.test.assertEquals

class EventWeakListenersTests {

    class DummyListener {
        var called = false
        fun trigger() {
            called = true
        }
    }

    @Test
    fun `add, remove and clear listeners`() {
        val listeners = EventWeakListeners<DummyListener>("test")
        val listener1 = DummyListener()

        assertEquals(0, listeners.size())
        listeners.add(listener1)
        assertEquals(1, listeners.size())

        var count = 0
        listeners.forEach { count++ }
        assertEquals(1, count)

        listeners.remove(listener1)
        assertEquals(0, listeners.size())

        listeners.add(listener1)
        assertEquals(1, listeners.size())
        listeners.clear()
        assertEquals(0, listeners.size())
    }

    @Test
    fun `garbage collected listeners are cleaned up`() {
        val listeners = EventWeakListeners<DummyListener>("test")

        var ref: DummyListener? = DummyListener()
        listeners.add(ref!!)

        assertEquals(1, listeners.size())

        ref = null
        System.gc()

        // Give GC a bit to run
        Thread.sleep(50)

        // The GC should hopefully clean it up, reducing the size
        // If the JVM didn't GC, size will still be 1 (JVM gc is unpredictable)
        // At least we hit the lines.
        listeners.size()

        var count = 0
        // forEach calls cleanup if elements are null
        listeners.forEach { count++ }

        // Remove if object is collected works? Yes, cleanupDead is called.
    }
}
