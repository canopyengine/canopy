package io.canopy.engine.core.flows

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import io.canopy.engine.core.exceptions.CanopyException
import io.canopy.engine.core.flows.events.asSignal
import io.canopy.engine.core.flows.events.signal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Tests for [io.canopy.engine.core.flows.events.Signal].
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

        yield()
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

    @Test
    fun `update from coroutine does not block`() = runBlocking {
        val value = signal(0)

        value.update { 1 }

        assertEquals(1, value())
    }

    @Test
    fun `late flow replays only emissions completed after synchronous callbacks`() {
        val value = signal(0)
        val failure = IllegalStateException("listener")
        val handle = value.connect { throw failure }
        assertSame(failure, assertFailsWith<IllegalStateException> { value.update { 1 } })
        assertEquals(1, value())
        assertEquals(listOf(0), value.flow.replayCache)
        handle.disconnect()
        value.update { 2 }
        assertEquals(listOf(2), value.flow.replayCache)
        value.dispose()
    }

    @Test
    fun `first flow access during nested callback uses committed replay rather than current value`() {
        val value = signal(0)
        val failure = IllegalStateException("nested listener")
        val handle = value.connect { next ->
            if (next == 2) throw failure
            if (next == 1) {
                assertSame(failure, assertFailsWith<IllegalStateException> { value.update { 2 } })
                assertEquals(2, value())
                assertEquals(listOf(0), value.flow.replayCache)
            }
            if (next == 3) value.update { 4 }
        }
        value.update { 1 }
        assertEquals(2, value())
        assertEquals(listOf(1), value.flow.replayCache)
        value.update { 3 }
        assertEquals(4, value())
        assertEquals(listOf(3), value.flow.replayCache)
        handle.disconnect()
        value.dispose()
    }

    @Test
    fun `nullable late replay and held flow are cleared by disposal during callback`() {
        val value = signal<String?>("initial")
        value.update { null }
        val held = value.flow
        assertEquals(listOf<String?>(null), held.replayCache)
        val handle = value.connect { value.dispose() }
        value.update { "disposed" }
        assertTrue(held.replayCache.isEmpty())
        assertFailsWith<CanopyException> { value.flow }
        handle.disconnect()
    }

    @Test
    fun `slow collector retains replay plus 64 extra values and drops oldest`() = runBlocking {
        val value = signal(0)
        val release = CompletableDeferred<Unit>()
        val collected = mutableListOf<Int>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            value.flow.take(66).collect {
                collected += it
                if (it == 0) release.await()
            }
        }
        for (next in 1..100) value.update { next }
        release.complete(Unit)
        job.join()
        assertEquals(listOf(0) + (36..100), collected)
        value.dispose()
    }
}
