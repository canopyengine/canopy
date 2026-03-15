package io.canopy.engine.core.flow.events

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
 * ## Reading values
 *
 * Both [value] and [invoke] register the signal as a dependency inside a [computed]
 * or [effect] block. Use [untrack] to read without registering.
 *
 * ## Emission semantics
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

    /** Backing field. */
    private var _value: T = initial

    /**
     * Current signal value.
     *
     * **Getting** registers this signal as a dependency in the active [TrackingContext]
     * frame (if any), then returns the current value.
     *
     * **Setting** a different value triggers:
     * - [valueChanged.emit]
     * - flow emission (blocking via runBlocking)
     *
     * Use [untrack] to read without registering a dependency.
     */
    var value: T
        get() {
            TrackingContext.register(this)
            return _value
        }
        set(new) {
            val old = _value
            if (old != new) {
                _value = new
                valueChanged.emit(new)
                runBlocking { _flow.emit(new) }
            }
        }

    init {
        // Make sure the initial value is available to flow collectors immediately.
        _flow.tryEmit(initial)
    }

    /**
     * Reads and returns the current value, also registering this signal as a dependency
     * in the active [TrackingContext] frame (if any).
     *
     * Equivalent to reading [value]. Provided as a concise shorthand inside reactive
     * blocks:
     * ```kotlin
     * val isDead = computed { hp() <= 0 }
     * ```
     */
    operator fun invoke(): T = value

    /** Subscribes a listener to value changes (weak reference). */
    infix fun connect(listener: (T) -> Unit) = valueChanged connect listener

    /** Unsubscribes a previously registered listener. */
    infix fun disconnect(listener: (T) -> Unit) = valueChanged disconnect listener

    /** Removes all listeners registered via [connect]. */
    fun clear() = valueChanged.clear()

    /** Updates the value based on the previous value. */
    fun update(handler: (T) -> T) {
        value = handler(_value)
    }
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
