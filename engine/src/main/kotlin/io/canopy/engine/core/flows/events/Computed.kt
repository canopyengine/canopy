package io.canopy.engine.core.flows.events

import kotlin.reflect.KProperty
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime

/**
 * Lazily evaluated cached computation. Creation ownership is independent of the first reader.
 * Declare `val hungry by computed { hunger < 20 }` and read it as an ordinary property.
 * Detached reads calculate without retaining subscriptions; reentered reads renew observation.
 * Node-local observation suspends on tree exit; later entered reads renew it. Destruction releases the calculation.
 * Derivations suppress ambient ownership and should be pure. All operations are game-thread confined.
 */
class Computed<T>(block: () -> T, owner: Node<*>? = NodeLifetime.current()) {
    private val lifetime = SourceLifetime(owner)
    private var action: (() -> T)? = block
    private var output: Signal<T>? = null
    private val subscriptions = DependencySubscriptions()
    private var recomputing = false
    init {
        lifetime.bind(::dispose, ::suspendObservation, ::recompute)
    }

    private fun signal(): Signal<T> {
        lifetime.check("read computed")
        check(lifetime.canObserve) { "Enter the owning Node before observing a computed flow or connection" }
        output?.let { return it }
        val result = runBlock()
        lifetime.check("finish computed")
        check(lifetime.canObserve) { "The owning Node exited during its calculation" }
        return Signal(result, owner = null).also { output = it }
    }

    /** Read-only changes stream; cancel owned collector jobs on removal. */
    val flow get() = signal().flow

    /** Installs read-only derived state in guarded Node storage. */
    operator fun provideDelegate(thisRef: Node<*>, property: KProperty<*>): ComputedProperty<T> =
        ComputedProperty(thisRef.reactiveSlot(this))

    /** Native read-only delegation outside Nodes preserves the source's explicit lifetime. */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = invoke()

    /** Reads tracked derived state while active; detached reads are fresh, pure, unobserved calculations. */
    operator fun invoke(): T {
        lifetime.check("read computed")
        return if (lifetime.canObserve) signal()() else untrack { action!!.invoke() }
    }

    private fun suspendObservation() {
        subscriptions.clear()
        output?.dispose()
        output = null
    }

    /** Connects using the caller's explicit or ambient tree lifetime. */
    infix fun connect(listener: (T) -> Unit) = signal().connect(listener)

    /** Connects using explicit tree-lifetime ownership. */
    fun connect(owner: Node<*>?, listener: (T) -> Unit) = signal().connect(owner, listener)

    /** Disconnects callback storage and ownership registrations. */
    infix fun disconnect(listener: (T) -> Unit) = signal().disconnect(listener)
    private fun recompute() {
        if (lifetime.disposed || !lifetime.active || recomputing) return
        recomputing = true
        try {
            output?.update { runBlock() }
        } finally {
            recomputing = false
        }
    }
    private fun runBlock(): T {
        lifetime.check("compute")
        val frame = TrackingContext.push()
        try {
            return NodeLifetime.withOwner(null) { action!!.invoke() }
        } finally {
            TrackingContext.pop()
            updateDependencies(frame)
        }
    }
    private fun updateDependencies(next: Set<Signal<*>>) {
        if (lifetime.disposed || !lifetime.active) return
        subscriptions.reconcile(next, ::recompute)
    }

    /** Idempotently releases dependency callbacks, cached output and captured calculation closure. */
    fun dispose() {
        if (!lifetime.dispose()) return
        suspendObservation()
        action = null
    }
}

/** Creates a computation capturing its creation owner. */
fun <T> computed(block: () -> T) = Computed(block)

/** Creates a computation with explicit ownership; null means shared. */
fun <T> computed(owner: Node<*>?, block: () -> T) = Computed(block, owner)
