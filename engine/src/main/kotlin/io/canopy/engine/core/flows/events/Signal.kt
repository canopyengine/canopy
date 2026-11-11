package io.canopy.engine.core.flows.events

import kotlin.reflect.KProperty
import java.lang.ref.WeakReference
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
 * - Assignments only emit when `old != new`; standard mutable collection edits also emit.
 * - Collection factories own a shallow copy. Element fields need their own reactive state.
 * - Collection Flow values are live views, not immutable snapshots of each edit.
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
class Signal<T> internal constructor(
    initial: T,
    owner: Node<*>?,
    private val adapt: ((T, () -> Unit, () -> Unit) -> T)?,
) {
    /** Low-level scalar source; use signal(collection) for observable standard collections. */
    constructor(initial: T, owner: Node<*>? = NodeLifetime.current()) : this(initial, owner, null)

    private val lifetime = SourceLifetime(owner)

    private val valueChanged = event<T>(owner = null)

    private val flowLock = Any()
    private var changes: MutableSharedFlow<T>? = null

    // Replay follows successful callback completion, independently of the current value, including null.
    private var flowReplay: Any? = null

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

    private var generation = 0L
    private var publishing = false
    private var collectionPending = false

    @Volatile private var value: Any? = prepare(initial)

    private fun prepare(next: T): T {
        val factory = adapt ?: return next
        val version = ++generation
        val weak = WeakReference(this)
        return untrack {
            factory(next, {
                weak.get()?.let { source ->
                    if (!source.lifetime.disposed && source.generation == version && source.lifetime.canObserve) {
                        TrackingContext.register(source)
                    }
                }
            }, {
                weak.get()?.let { source ->
                    if (!source.lifetime.disposed && source.generation == version) source.collectionChanged()
                }
            })
        }
    }

    private fun collectionChanged() {
        lifetime.check("mutate signal collection")
        if (!lifetime.active) {
            flowReplay = value
            return
        }
        collectionPending = true
        if (publishing) return
        publishing = true
        try {
            while (collectionPending) {
                collectionPending = false
                publish()
            }
        } finally {
            publishing = false
        }
    }

    private fun publish() {
        @Suppress("UNCHECKED_CAST")
        val current = value as T
        valueChanged.emit(current)
        synchronized(flowLock) {
            if (!lifetime.disposed) {
                flowReplay = current
                changes?.tryEmit(current)
            }
        }
    }

    init {
        flowReplay = value
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
        if (untrack { old != new }) {
            value = prepare(new)
            if (!lifetime.active) {
                flowReplay = value
                return
            }
            if (adapt == null) publish() else collectionChanged()
        }
    }

    /** Subscribes to changes; the matching compiler infers the listening Node inside Node methods/callbacks. */
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

/** Creates owned read-only list state; assign a replacement list to update it. */
fun <E> signal(value: List<E>): Signal<List<E>> = signal(NodeLifetime.current(), value)

/** Creates owned read-only list state with explicit lifetime. */
fun <E> signal(owner: Node<*>?, value: List<E>): Signal<List<E>> =
    Signal(value, owner) { items, read, changed -> ObservableList(items, read, changed) }

/** Creates owned mutable list state; iterator and sublist mutations also notify. */
@JvmName("mutableListSignal")
fun <E> signal(value: MutableList<E>): Signal<MutableList<E>> = signal(NodeLifetime.current(), value)

/** Creates owned mutable list state with explicit lifetime; input aliases cannot mutate it. */
@JvmName("ownedMutableListSignal")
fun <E> signal(owner: Node<*>?, value: MutableList<E>): Signal<MutableList<E>> =
    Signal(value, owner) { items, read, changed -> ObservableList(items, read, changed) }

/** Creates owned read-only set state. */
fun <E> signal(value: Set<E>): Signal<Set<E>> = signal(NodeLifetime.current(), value)

/** Creates owned read-only set state with explicit lifetime. */
fun <E> signal(owner: Node<*>?, value: Set<E>): Signal<Set<E>> =
    Signal(value, owner) { items, read, changed -> ObservableSet(items, read, changed) }

/** Creates owned mutable set state; duplicate additions and absent removals do not notify. */
@JvmName("mutableSetSignal")
fun <E> signal(value: MutableSet<E>): Signal<MutableSet<E>> = signal(NodeLifetime.current(), value)

/** Creates owned mutable set state with explicit lifetime. */
@JvmName("ownedMutableSetSignal")
fun <E> signal(owner: Node<*>?, value: MutableSet<E>): Signal<MutableSet<E>> =
    Signal(value, owner) { items, read, changed -> ObservableSet(items, read, changed) }

/** Creates owned read-only map state. */
fun <K, V> signal(value: Map<K, V>): Signal<Map<K, V>> = signal(NodeLifetime.current(), value)

/** Creates owned read-only map state with explicit lifetime. */
fun <K, V> signal(owner: Node<*>?, value: Map<K, V>): Signal<Map<K, V>> =
    Signal(value, owner) { items, read, changed -> ObservableMap(items, read, changed) }

/** Creates owned mutable map state, including entry, key and value view mutations. */
@JvmName("mutableMapSignal")
fun <K, V> signal(value: MutableMap<K, V>): Signal<MutableMap<K, V>> = signal(NodeLifetime.current(), value)

/** Creates owned mutable map state with explicit lifetime. */
@JvmName("ownedMutableMapSignal")
fun <K, V> signal(owner: Node<*>?, value: MutableMap<K, V>): Signal<MutableMap<K, V>> =
    Signal(value, owner) { items, read, changed -> ObservableMap(items, read, changed) }
