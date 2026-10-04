package io.canopy.engine.core.flows.events

import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeLifetime

/**
 * Synchronous, game-thread-confined event. Unowned listeners remain weak; retain a handle to retain a callback.
 * Managed connections are tree-owned. Sources capture creation ownership and dispose on permanent destruction.
 * Listener calls suppress ambient ownership; nested resources require explicit owners.
 */
sealed class Event<T : Any>(owner: Node<*>?) {
    private val lifetime = SourceLifetime(owner)
    internal val connections = EventConnections<T>()
    init {
        lifetime.bind(::dispose)
    }
    protected fun requireOpen(operation: String) {
        lifetime.check(operation)
    }

    /** Removes every listener and its ownership registration. */
    fun clear() {
        requireOpen("clear event")
        connections.clear()
    }

    /** Number of live callbacks, pruning dead weak references. */
    fun size(): Int {
        requireOpen("event size")
        return connections.size()
    }

    /** Whether there are no live listeners. */
    fun isEmpty(): Boolean = size() == 0

    /** Connects under the current managed scope, or returns an unowned retained handle. */
    infix fun connect(listener: T): EventDisconnectHandler {
        requireOpen("connect event")
        return connections.connect(listener)
    }

    /** Connects with explicit tree-lifetime ownership. */
    fun connect(owner: Node<*>?, listener: T): EventDisconnectHandler =
        NodeLifetime.withOwner(owner) { connect(listener) }

    /** Disconnects all registrations of the same listener identity, including owned handles. */
    infix fun disconnect(listener: T) {
        requireOpen("disconnect event")
        connections.disconnect(listener)
    }

    /** Idempotent permanent disposal; callable during destruction. */
    fun dispose() {
        if (lifetime.dispose()) connections.clear()
    }
}

/** Zero-argument source, node-local unless constructed with an explicit null owner. */
class NoArgEvent(owner: Node<*>? = NodeLifetime.current()) : Event<() -> Unit>(owner) {
    /** Synchronously invokes live listeners in registration order. */
    fun emit() {
        requireOpen("emit event")
        connections.emit { it() }
    }
}

/** One-argument source. */
class OneArgEvent<A>(owner: Node<*>? = NodeLifetime.current()) : Event<(A) -> Unit>(owner) {
    /** Synchronously invokes live listeners; disconnections take effect during emission. */
    fun emit(value: A) {
        requireOpen("emit event")
        connections.emit { it(value) }
    }
}

/** Two-argument source. */
class TwoArgsEvent<A, B>(owner: Node<*>? = NodeLifetime.current()) : Event<(A, B) -> Unit>(owner) {
    /** Synchronously invokes live listeners. */
    fun emit(first: A, second: B) {
        requireOpen("emit event")
        connections.emit { it(first, second) }
    }
}

/** Creates a node-local source in a managed callback, otherwise a shared source. */
fun event() = NoArgEvent()

/** Creates a source with explicit ownership; null means shared. */
fun event(owner: Node<*>?) = NoArgEvent(owner)

/** Creates a one-argument source using creation ownership. */
fun <A> event() = OneArgEvent<A>()

/** Creates a one-argument source with explicit ownership. */
fun <A> event(owner: Node<*>?) = OneArgEvent<A>(owner)

/** Creates a two-argument source using creation ownership. */
fun <A, B> event() = TwoArgsEvent<A, B>()

/** Creates a two-argument source with explicit ownership. */
fun <A, B> event(owner: Node<*>?) = TwoArgsEvent<A, B>(owner)
