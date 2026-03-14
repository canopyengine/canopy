package io.canopy.engine.core.flow

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import io.canopy.engine.core.flow.events.event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Tests for the weak-listener event system.
 *
 * The event system guarantees:
 * - listeners receive emitted values
 * - disconnecting a listener stops future notifications
 * - disconnecting one listener does not affect others
 * - emissions are safe under concurrent producers
 * - clear() removes all listeners
 */
class EventTests {

    @Test
    fun `callback should be called on signal emission`() {
        // Create a 1-argument signal
        val signal = event<Int>()

        // Capture the value received by the listener
        var receivedValue: Int? = null
        val callback: (Int) -> Unit = { value -> receivedValue = value }

        // Register listener
        val connection = signal connect callback

        // Emit a value — listener should receive it
        signal.emit(42)
        assert(receivedValue == 42) { "Listener should have received the emitted value." }

        // Disconnect listener
        connection.disconnect()

        // Reset and emit again
        receivedValue = null
        signal.emit(100)

        // Listener should not be triggered after disconnection
        assert(receivedValue == null) { "Listener should not receive value after disconnection." }
    }

    @Test
    fun `signal disconnection shouldn't impact other listeners`() {
        val signal = event<Int>()

        var receivedByFirst: Int? = null
        var receivedBySecond: Int? = null

        val firstCallback: (Int) -> Unit = { value -> receivedByFirst = value }
        val secondCallback: (Int) -> Unit = { value -> receivedBySecond = value }

        // Register both listeners
        val firstConnection = signal connect firstCallback
        signal connect secondCallback

        // Both listeners should receive the emission
        signal.emit(10)
        assert(receivedByFirst == 10) { "First callback should have received 10" }
        assert(receivedBySecond == 10) { "Second callback should have received 10" }

        // Disconnect the first listener
        firstConnection.disconnect()

        // Reset state
        receivedByFirst = null
        receivedBySecond = null

        // Emit again
        signal.emit(20)

        // Only the second listener should receive the value
        assert(receivedByFirst == null) { "First callback should not receive value after disconnection" }
        assert(receivedBySecond == 20) { "Second callback should still receive emitted value" }
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun `test signal thread-safety`() = runBlocking {
        /**
         * Verifies that the event system behaves correctly under concurrent emission.
         *
         * Multiple threads emit values simultaneously and the listener aggregates them
         * using an atomic counter.
         */

        val container = AtomicInt(0)

        val signal = event<Int>()
        val callback: (Int) -> Unit = { value -> container.addAndFetch(value) }

        signal connect callback

        // Launch multiple concurrent producers
        val producers =
            List(10) {
                launch(Dispatchers.Default) {
                    repeat(1000) {
                        signal.emit(1)
                    }
                }
            }

        // Wait for all producers to finish
        producers.joinAll()

        // Expected result: 10 threads × 1000 emits
        assert(container.load() == 10_000) {
            "Container should be 10000 but was ${container.load()}"
        }
    }

    @Test
    fun `test signal clear`() {
        /**
         * Verifies that clear() removes all registered listeners.
         */

        val signal = event<Int>()

        var callCount = 0
        val callback: (Int) -> Unit = { _ -> callCount++ }

        signal connect callback

        // First emission should trigger listener
        signal.emit(42)

        // Clear all listeners
        signal.clear()

        // Second emission should trigger nothing
        signal.emit(100)

        assert(callCount == 1) {
            "Listener should have been called only once before clear."
        }
    }
}
