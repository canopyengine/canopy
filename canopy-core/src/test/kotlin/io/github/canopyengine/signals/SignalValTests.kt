package anchors.framework.utils.signals

import canopy.core.signals.SignalVal
import canopy.core.signals.asSignalVal
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

class SignalValTests {
    @Test
    fun `test signal val`() {
        val signalVal = SignalVal(0) // or 0.asSignalVal()

        var receivedValue: Int? = null
        val callback: (Int) -> Unit = { value -> receivedValue = value }

        signalVal connect callback

        signalVal.value = 42

        assert(receivedValue == 42) { "Listener should have received the emitted value." }
    }

    @Test
    fun `test signal val no callback on same value`() {
        val signalVal = SignalVal(0) // or 0.asSignalVal()

        var callCount = 0
        val callback: (Int) -> Unit = { _ -> callCount++ }

        signalVal connect callback

        signalVal.value = 42
        signalVal.value = 42
        signalVal.value = 42

        assert(callCount == 1) { "Listener should have been called only once." }
    }

    @Test
    fun `test signal val disconnect`() {
        val signalVal = SignalVal(0) // or 0.asSignalVal()

        var callCount = 0
        val callback: (Int) -> Unit = { _ -> callCount++ }

        signalVal connect callback

        signalVal.value = 42
        signalVal disconnect callback
        signalVal.value = 100

        assert(callCount == 1) { "Listener should have been called only once before disconnection." }
    }

    @Test
    fun `test signal val clear`() {
        val signalVal = SignalVal(0) // or 0.asSignalVal()

        var callCount = 0
        val callback: (Int) -> Unit = { _ -> callCount++ }

        signalVal connect callback

        signalVal.value = 42
        signalVal.clear()
        signalVal.value = 100

        assert(callCount == 1) { "Listener should have been called only once before clear." }
    }

    @Test
    fun `test signal val flow`() =
        runBlocking {
            val signalVal = SignalVal(0)

            val collectedValues = mutableListOf<Int>()

            // Start collecting immediately
            val job =
                launch {
                    signalVal.flow.collect {
                        collectedValues.add(it)
                    }
                }

            // Update values
            signalVal.value = 42
            signalVal.value = 100
            signalVal.value = 100 // duplicate, should not emit (SharedFlow allows it, duplicates may remain)

            // Give coroutine a chance to collect all
            kotlinx.coroutines.yield()
            job.cancel() // Stop collecting

            assertEquals(listOf(0, 42, 100), collectedValues)
        }

    @Test
    fun `test signal val unwrapped`() {
        val signalVal = 10.asSignalVal()
        assertEquals(10, signalVal.value) { "Unwrapped value should match the current value." }

        signalVal.value = 20
        assertEquals(20, signalVal.value) { "Unwrapped value should update with the current value." }
    }
}
