package anchors.framework.utils.signals

import anchors.framework.signals.createSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test

class SignalsTests {
    @Test
    fun `callback should be called on signal emission`() {
        val signal = createSignal<Int>()

        var receivedValue: Int? = null
        val callback: (Int) -> Unit = { value -> receivedValue = value }

        signal connect callback
        signal.emit(42)

        assert(receivedValue == 42) { "Listener should have received the emitted value." }

        signal disconnect callback
        receivedValue = null
        signal.emit(100)

        assert(receivedValue == null) { "Listener should not receive value after disconnection." }
    }

    @Test
    fun `signal disconnection shouldn't impact other listeners`() {
        val signal = createSignal<Int>()

        var receivedByFirst: Int? = null
        var receivedBySecond: Int? = null

        val firstCallback: (Int) -> Unit = { value -> receivedByFirst = value }
        val secondCallback: (Int) -> Unit = { value -> receivedBySecond = value }

        // Connect both callbacks
        signal connect firstCallback
        signal connect secondCallback

        // Emit a value, both should receive it
        signal.emit(10)
        assert(receivedByFirst == 10) { "First callback should have received 10" }
        assert(receivedBySecond == 10) { "Second callback should have received 10" }

        // Disconnect the first callback
        signal disconnect firstCallback

        // Reset
        receivedByFirst = null
        receivedBySecond = null

        // Emit another value
        signal.emit(20)
        assert(receivedByFirst == null) { "First callback should not receive value after disconnection" }
        assert(receivedBySecond == 20) { "Second callback should still receive emitted value" }
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun `test signal thread-safety`() =
        runBlocking {
            val container = AtomicInt(0)

            val signal = createSignal<Int>()
            val callback: (Int) -> Unit = { value -> container.addAndFetch(value) }

            // Connect listener
            signal connect callback // signal.connect(callback)

            // Launch multiple concurrent producers on different threads
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
            // Verify result
            assert(container.load() == 10_000) { "Container should be 10000 but was ${container.load()}" }
        }

    @Test
    fun `test signal clear`() {
        val signal = createSignal<Int>()

        var callCount = 0
        val callback: (Int) -> Unit = { _ -> callCount++ }

        signal connect callback

        signal.emit(42)
        signal.clear()
        signal.emit(100)

        assert(callCount == 1) { "Listener should have been called only once before clear." }
    }
}
