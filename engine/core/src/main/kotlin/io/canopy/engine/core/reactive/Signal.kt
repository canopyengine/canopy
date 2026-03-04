package io.canopy.engine.core.reactive

import kotlin.properties.Delegates
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.runBlocking

/**
 * A mutable value that notifies observers when it changes.
 *
 * This class exposes two observation APIs:
 *
 * 1) Callback/event style via [connect]:
 *    - Lightweight, synchronous notification.
 *    - Listeners are stored as weak references (see [event]).
 *
 * 2) Flow style via [flow]:
 *    - Kotlin Flow stream with replay = 1 (new collectors receive the latest value).
 *    - [distinctUntilChanged] avoids emitting duplicates.
 *
 * Emission semantics:
 * - Updates only emit when `old != new`.
 * - The event listeners are notified immediately.
 * - The flow emission uses `runBlocking { emit(...) }` which means setting [value]
 *   may block the calling thread if collectors are slow or the flow suspends.
 *
 * (This is acceptable in some engine contexts, but contributors should be aware.)
 *
 * @param initial Initial value of the signal (also emitted immediately to [flow]).
 */
class Signal<T>(initial: T) {

    /** Weak-listener event fired when [value] changes. */
    private val valueChanged = event<T>()

    /**
     * SharedFlow that replays the latest value to new subscribers.
     *
     * replay = 1 means collectors always start with the most recent value.
     */
    private val _flow = MutableSharedFlow<T>(replay = 1)

    /**
     * Public flow view, filtered so it only emits when the value actually changes.
     */
    val flow = _flow.asSharedFlow().distinctUntilChanged()

    /**
     * Current signal value.
     *
     * Assigning a different value triggers:
     * - [valueChanged.emit]
     * - flow emission (blocking via runBlocking)
     */
    var value: T by Delegates.observable(initial) { _, old, new ->
        if (old != new) {
            valueChanged.emit(new)
            runBlocking { _flow.emit(new) }
        }
    }

    init {
        // Make sure the initial value is available to flow collectors immediately.
        _flow.tryEmit(initial)
    }

    /** Subscribes a listener to value changes (weak reference). */
    infix fun connect(listener: (T) -> Unit) = valueChanged connect listener

    /** Unsubscribes a previously registered listener. */
    infix fun disconnect(listener: (T) -> Unit) = valueChanged disconnect listener

    /** Removes all listeners registered via [connect]. */
    fun clear() = valueChanged.clear()
}

/* ------------------------------------------------------------------
 * Convenience factory helpers
 * ------------------------------------------------------------------ */

/** Wraps any value into a [Signal]. */
fun <T> T.asSignal() = signal(this)

/** Creates a new [Signal] from [value]. */
fun <T> signal(value: T) = Signal(value)

/** Convenience for nullable signals starting as null. */
fun <T> Nothing?.asSignal() = signal<T?>(null)
