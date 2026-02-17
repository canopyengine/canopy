package canopy.core.signals

import kotlin.properties.Delegates
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.runBlocking

/**
 * A variable that emits_ a signal when its value changes.
 * @property value The current value of the variable.
 * @property flow A StateFlow that emits the current value and updates on changes.
 * Useful for data binding and reactive programming.
 */
class SignalVal<T>(initial: T) {
    private val valueChanged = createSignal<T>()
    private val _flow = MutableSharedFlow<T>(replay = 1) // replay last value
    val flow = _flow.asSharedFlow().distinctUntilChanged() // distinct until changed

    var value: T by Delegates.observable(initial) { _, old, new ->
        if (old != new) {
            valueChanged.emit(new)
            runBlocking { _flow.emit(new) }
        }
    }

    init {
        _flow.tryEmit(initial) // emit initial value
    }

    infix fun connect(listener: (T) -> Unit) = valueChanged connect listener

    infix fun disconnect(listener: (T) -> Unit) = valueChanged disconnect listener

    fun clear() = valueChanged.clear()
}

// Convenience functions to create SignalVal instances
fun <T> T.asSignalVal() = SignalVal(this)

fun <T> Nothing?.asNullableSignalVal() = SignalVal<T?>(null)
