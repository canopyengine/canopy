package io.canopy.engine.core.flows.events

import kotlin.reflect.KProperty
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A reactive value container that notifies observers when it changes.
 *
 * Declare gameplay state with `var hunger by signal(100)`, read `hunger`, and assign `hunger = 80`.
 * Node delegates keep their source in guarded engine storage. Values survive reusable detachment;
 * callbacks and Flow publication disconnect on exit and must be renewed after entry. Flow collector
 * jobs remain owned by their caller; register cancellation with the appropriate Node.
 * Initial construction may prepare dormant bindings; updates publish only while the owner is entered.
 * Explicit Signal objects retain invoke/update/connect/flow access for low-level integrations.
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
 * - Flow emission is non-blocking; slow collectors may skip intermediate values.
 *
 * ## Threading
 *
 * Signals are intended to be read and updated from one serialized thread, usually
 * the game thread. The current value is volatile for visibility, but concurrent
 * read-modify-write calls to [update] are not atomic. Listener callbacks run
 * synchronously on the thread that calls [update].
 *
 * @param initial Initial value of the signal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Signal<T>(initial: T, owner: Node<*>? = NodeLifetime.current()) {
    private val lifetime = SourceLifetime(owner)

    private val valueChanged = event<T>(owner = null)

    private val flowLock = Any()
    private var changes: MutableSharedFlow<T>? = null

    // Replay follows successful callback completion, independently of the current value, including null.
    private var flowReplay: Any? = initial

    /** Read-only stream; owned collectors must register their job for cancellation. */
    val flow get() = synchronized(flowLock) {
        lifetime.check("read signal flow")
        check(lifetime.canObserve) { "Enter the owning Node before observing its signal flow" }
        val stream = changes ?: MutableSharedFlow<T>(
            replay = 1,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        ).also {
            @Suppress("UNCHECKED_CAST")
            it.tryEmit(flowReplay as T)
            changes = it
        }
        stream.asSharedFlow()
    }

    @Volatile private var value: Any? = initial

    init {
        lifetime.bind(::dispose, ::suspendObservation, ::resumeObservation)
    }

    /** Installs a Node property without retaining its source on the Node facade. */
    operator fun provideDelegate(thisRef: Node<*>, property: KProperty<*>): SignalProperty<T> =
        SignalProperty(thisRef.reactiveSlot(this))

    /** Native delegation outside Nodes retains the caller's explicitly chosen source lifetime. */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = invoke()

    /** Native assignment outside Nodes uses the same equality and notification behavior. */
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = update { value }

    private fun resumeObservation() {
        @Suppress("UNCHECKED_CAST")
        val current = value as T
        valueChanged.emit(current)
        synchronized(flowLock) {
            flowReplay = current
            changes?.tryEmit(current)
        }
    }

    private fun suspendObservation() {
        valueChanged.clear()
        synchronized(flowLock) {
            changes?.resetReplayCache()
            changes = null
            flowReplay = value
        }
    }

    /**
     * Reads the current value and registers this signal as a dependency in the active
     * [TrackingContext] frame (if any).
     *
     * Use this inside [computed] and [effect] blocks to declare reactive dependencies.
     * Use [untrack] to read the current value without registering a dependency.
     */
    operator fun invoke(): T {
        lifetime.check("read signal")
        if (lifetime.canObserve) TrackingContext.register(this)
        @Suppress("UNCHECKED_CAST")
        return value as T
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
        lifetime.check("update signal")
        @Suppress("UNCHECKED_CAST")
        val new = handler(value as T)
        lifetime.check("update signal")
        val old = value
        if (old != new) {
            value = new
            if (!lifetime.active) {
                flowReplay = new
                return
            }
            valueChanged.emit(new)
            synchronized(flowLock) {
                if (!lifetime.disposed) {
                    flowReplay = new
                    changes?.tryEmit(new)
                }
            }
        }
    }

    /** Subscribes a listener to value changes (weak reference). */
    infix fun connect(listener: (T) -> Unit): EventDisconnectHandler {
        lifetime.check("connect signal")
        check(lifetime.canObserve) { "Enter the owning Node before connecting to its signal" }
        return valueChanged connect listener
    }

    /** Subscribes with automatic disconnection when [owner] exits or is removed. */
    fun connect(owner: Node<*>?, listener: (T) -> Unit): EventDisconnectHandler {
        lifetime.check("connect signal")
        check(lifetime.canObserve) { "Enter the owning Node before connecting to its signal" }
        return valueChanged.connect(owner, listener)
    }

    /** Unsubscribes a previously registered listener. */
    infix fun disconnect(listener: (T) -> Unit) = valueChanged disconnect listener

    /** Removes all listeners registered via [connect]. */
    fun clear() {
        lifetime.check("clear signal")
        valueChanged.clear()
    }

    /** Permanently releases listeners, cached value and replay contents. Shared signals require explicit disposal. */
    fun dispose() = synchronized(flowLock) {
        if (!lifetime.dispose()) return@synchronized
        valueChanged.dispose()
        value = null
        flowReplay = null
        changes?.resetReplayCache()
        changes = null
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

/** Creates a signal with explicit ownership; null preserves a shared lifetime. */
fun <T> signal(owner: Node<*>?, value: T) = Signal(value, owner)
