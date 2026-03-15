package io.canopy.engine.core.flow

import kotlin.test.Test
import io.canopy.engine.core.flow.events.asSignal
import io.canopy.engine.core.flow.events.signal
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Tests for [io.canopy.engine.core.flow.events.Signal].
 *
 * Signal is read via `signal()` (invoke) and written via `signal.update { }`.
 */
class SignalTests {

    @Test
    fun `signal should notify listeners on value change`() {
        val signal = signal(0)

        var receivedValue: Int? = null
        val callback: (Int) -> Unit = { value -> receivedValue = value }

        signal connect callback

        signal.update { it + 42 }

        assert(receivedValue == 42) { "Listener should have received the emitted value." }
    }

    @Test
    fun `signal should not notify listeners when setting same value`() {
        val signal = signal(0)

        var callCount = 0
        val callback: (Int) -> Unit = { _ -> callCount++ }

        signal connect callback

        signal.update { 42 }

        // Updating to the same value should NOT notify
        signal.update { 42 }
        signal.update { 42 }

        assert(callCount == 1) { "Listener should have been called only once." }
    }

    @Test
    fun `signal disconnect should stop notifications`() {
        val signal = signal(0)

        var callCount = 0
        val callback: (Int) -> Unit = { _ -> callCount++ }

        signal connect callback

        signal.update { 42 }

        signal disconnect callback
        signal.update { 100 }

        assert(callCount == 1) { "Listener should have been called only once before disconnection." }
    }

    @Test
    fun `signal clear should remove all listeners`() {
        val signal = signal(0)

        var callCount = 0
        val callback: (Int) -> Unit = { _ -> callCount++ }

        signal connect callback

        signal.update { 42 }

        signal.clear()

        signal.update { 100 }

        assert(callCount == 1) { "Listener should have been called only once before clear." }
    }

    @Test
    fun `signal flow should replay initial and emit distinct values`() = runBlocking {
        val signal = signal(0)

        val collectedValues = mutableListOf<Int>()

        val job = launch {
            signal.flow.collect { collectedValues.add(it) }
        }

        signal.update { 42 }
        signal.update { 100 }
        signal.update { 100 } // duplicate -> should not be collected (distinctUntilChanged)

        yield()
        job.cancel()

        assertEquals(listOf(0, 42, 100), collectedValues)
    }

    @Test
    fun `asSignal should wrap a value and allow updates`() {
        val signal = 10.asSignal()

        assertEquals(10, signal()) { "Wrapped value should match the initial value." }

        signal.update { 20 }
        assertEquals(20, signal()) { "Wrapped value should update when assigned." }
    }

    @Test
    fun `update receives current value as argument`() {
        val signal = signal(10)

        signal.update { it + 5 }

        assertEquals(15, signal())
    }
}
