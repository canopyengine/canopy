package io.canopy.engine.core.flows.events

import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime

/**
 * Lazily evaluated cached computation. Creation ownership is independent of the first reader.
 * Node-local computations dispose on tree exit; shared computations require explicit disposal.
 * Derivations suppress ambient ownership and should be pure. All operations are game-thread confined.
 */
class Computed<T>(block: () -> T, owner: Node<*>? = NodeLifetime.current()) {
    private val lifetime = SourceLifetime(owner)
    private var action: (() -> T)? = block
    private var output: Signal<T>? = null
    private var dependencies = emptySet<Signal<*>>()
    private val handlers = mutableMapOf<Signal<*>, EventDisconnectHandler>()
    private var recomputing = false
    private var cancelRemoval: (() -> Unit)? = null
    init {
        lifetime.bind(::dispose)
        cancelRemoval = owner?.onRemoval(::dispose)
    }

    private fun signal(): Signal<T> {
        lifetime.check("read computed")
        return output ?: Signal(runBlock(), owner = null).also { output = it }
    }

    /** Read-only changes stream; cancel owned collector jobs on removal. */
    val flow get() = signal().flow

    /** Reads the cached value and participates in dependency tracking. */
    operator fun invoke(): T = signal()()

    /** Connects using the caller's explicit or ambient tree lifetime. */
    infix fun connect(listener: (T) -> Unit) = signal().connect(listener)

    /** Connects using explicit tree-lifetime ownership. */
    fun connect(owner: Node<*>?, listener: (T) -> Unit) = signal().connect(owner, listener)

    /** Disconnects callback storage and ownership registrations. */
    infix fun disconnect(listener: (T) -> Unit) = signal().disconnect(listener)
    private fun recompute() {
        if (lifetime.disposed || recomputing) return
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
        if (lifetime.disposed) return
        for (dep in dependencies - next) handlers.remove(dep)?.disconnect()
        for (dep in next - dependencies) {
            handlers[dep] = NodeLifetime.withOwner(null) { dep.connect { _ -> recompute() } }
        }
        dependencies = next
    }

    /** Idempotently releases dependency callbacks, cached output and captured calculation closure. */
    fun dispose() {
        if (!lifetime.dispose()) return
        cancelRemoval?.invoke()
        cancelRemoval = null
        handlers.values.forEach { it.disconnect() }
        handlers.clear()
        dependencies = emptySet()
        output?.dispose()
        output = null
        action = null
    }
}

/** Creates a computation capturing its creation owner. */
fun <T> computed(block: () -> T) = Computed(block)

/** Creates a computation with explicit ownership; null means shared. */
fun <T> computed(owner: Node<*>?, block: () -> T) = Computed(block, owner)
