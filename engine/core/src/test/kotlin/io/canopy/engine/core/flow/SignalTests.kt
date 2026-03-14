package io.canopy.engine.core.flow

import kotlin.test.Test
import io.canopy.engine.core.flow.events.asSignal
import io.canopy.engine.core.flow.events.signal
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Tests for [io.canopy.engine.core.flow.events.Signal], a mutable value that notifies observers when it changes.
 *
 * Signal supports two observation mechanisms:
 * - callback/event style via `connect` (weak listeners)
 * - Kotlin Flow via `flow` (replay = 1, distinctUntilChanged)
 *
 * These tests document the expected contracts.
 */
class SignalTests {

    @Test
    fun `signal should notify listeners on value change`() {
        val signal = signal(0)

        var receivedValue: Int? = null
        val callback: (Int) -> Unit = { value -> receivedValue = value }

        // Register listener
        signal connect callback

        // Mutate the signal
        signal.value += 42

        // Listener should receive the new value
        assert(receivedValue == 42) { "Listener should have received the emitted value." }
    }

    @Test
    fun `signal should not notify listeners when setting same value`() {
        val signal = signal(0)

        var callCount = 0
        val callback: (Int) -> Unit = { _ -> callCount++ }

        signal connect callback

        // First change should notify
        signal.value = 42

        // Reassigning the same value should NOT notify
        signal.value = 42
        signal.value = 42

        assert(callCount == 1) { "Listener should have been called only once." }
    }

    @Test
    fun `signal disconnect should stop notifications`() {
        val signal = signal(0)

        var callCount = 0
        val callback: (Int) -> Unit = { _ -> callCount++ }

        signal connect callback

        // Listener should be called once
        signal.value = 42

        // After disconnect, listener should no longer be invoked
        signal disconnect callback
        signal.value = 100

        assert(callCount == 1) { "Listener should have been called only once before disconnection." }
    }

    @Test
    fun `signal clear should remove all listeners`() {
        val signal = signal(0)

        var callCount = 0
        val callback: (Int) -> Unit = { _ -> callCount++ }

        signal connect callback

        // First update triggers listener
        signal.value = 42

        // Clear removes all listeners
        signal.clear()

        // Further updates should not trigger the callback
        signal.value = 100

        assert(callCount == 1) { "Listener should have been called only once before clear." }
    }

    @Test
    fun `signal flow should replay initial and emit distinct values`() = runBlocking {
        val signal = signal(0)

        val collectedValues = mutableListOf<Int>()

        // Collect from the flow:
        // - replay = 1 means we should immediately receive the current value (0)
        // - distinctUntilChanged means duplicates should not be emitted
        val job = launch {
            signal.flow.collect { collectedValues.add(it) }
        }

        // Update values
        signal.update { 42 }
        signal.value = 100
        signal.update { 100 } // duplicate -> should not be collected (distinctUntilChanged)

        // Give collector a chance to run
        yield()
        job.cancel()

        assertEquals(listOf(0, 42, 100), collectedValues)
    }

    @Test
    fun `asSignal should wrap a value and allow updates`() {
        val signal = 10.asSignal()

        // Initial value should be preserved
        assertEquals(10, signal.value) { "Wrapped value should match the initial value." }

        // Updating the signal should update its stored value
        signal.update { 20 }
        assertEquals(20, signal.value) { "Wrapped value should update when assigned." }
    }
}
