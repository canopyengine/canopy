package io.canopy.engine.core.flow.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking

/**
 * A reactive value container that notifies observers when it changes.
 *
 * ## Reading
 *
 * Values are read by invoking the signal as a function:
 * ```kotlin
 * val hp = signal(100)
 * val current = hp()   // reads the value; also registers a dependency if inside computed/effect
 * ```
 *
 * ## Writing
 *
 * Values are updated via [update]:
 * ```kotlin
 * hp.update { 0 }           // set to 0
 * hp.update { it - 10 }     // decrement by 10
 * ```
 *
 * ## Observation
 *
 * In addition to reactive tracking via `signal()`, two explicit observation APIs are provided:
 *
 * 1) Callback/event style via [connect] — lightweight, synchronous, weak-referenced.
 * 2) Flow style via [flow] — Kotlin MutableSharedFlow with replay = 1.
 *
 * ## Emission semantics
 * - Updates only emit when `old != new`.
 * - The event listeners are notified immediately.
 * - Flow emission uses `runBlocking { emit(...) }` which may block the calling thread if
 *   collectors are slow or the flow suspends.
 *
 * @param initial Initial value of the signal.
 */
class Signal<T>(initial: T) {

    private val valueChanged = event<T>()

    /**
     * Kotlin Flow of value changes (replay = 1).
     *
     * New collectors immediately receive the current value.
     */
    val flow = MutableSharedFlow<T>(replay = 1)

    private var value: T = initial

    init {
        flow.tryEmit(initial)
    }

    /**
     * Reads the current value and registers this signal as a dependency in the active
     * [TrackingContext] frame (if any).
     *
     * Use this inside [computed] and [effect] blocks to declare reactive dependencies.
     * Use [untrack] to read the current value without registering a dependency.
     */
    operator fun invoke(): T {
        TrackingContext.register(this)
        return value
    }

    /**
     * Updates the value by applying [handler] to the current value.
     *
     * If the result is equal to the current value, nothing is emitted.
     *
     * ```kotlin
     * hp.update { 0 }           // set to a fixed value
     * hp.update { it - 10 }     // transform based on current value
     * ```
     */
    fun update(handler: (T) -> T) {
        val new = handler(value)
        val old = value
        if (old != new) {
            value = new
            valueChanged.emit(new)
            runBlocking { flow.emit(new) }
        }
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
