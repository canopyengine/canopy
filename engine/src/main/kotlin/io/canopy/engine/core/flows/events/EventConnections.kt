package io.canopy.engine.core.flows.events

import java.lang.ref.WeakReference
import io.canopy.engine.core.nodes.NodeLifetime

/** Weak unowned listeners, strong retained handles, O(1) handle removal and mutation-only snapshots. */
internal class EventConnections<T : Any> {
    /** Old dispatch snapshots retain neither listeners nor cancellation handles strongly. */
    private class Connection<T : Any>(val listener: WeakReference<T>, val handle: WeakReference<EventDisconnectHandler>)
    private val connections = linkedMapOf<Long, Connection<T>>()
    private var sequence = 0L
    private var snapshot: List<Pair<Long, Connection<T>>>? = null
    fun connect(listener: T): EventDisconnectHandler {
        NodeLifetime.current()?.requireValid("connect listener")
        val id = ++sequence
        val handle = EventDisconnectHandler({
            connections.remove(id)
            snapshot = null
        }, listener)
        connections[id] = Connection(WeakReference(listener), WeakReference(handle))
        snapshot = null
        return handle.owned()
    }
    private fun disconnect(id: Long, connection: Connection<T>) {
        connection.handle.get()?.disconnect()
        connections.remove(id)
        snapshot = null
    }
    fun disconnect(listener: T) {
        for ((id, connection) in entries()) {
            val current = connection.listener.get()
            if (current == null || current === listener) disconnect(id, connection)
        }
    }
    private fun entries(): List<Pair<Long, Connection<T>>> = snapshot
        ?: connections.map { it.key to it.value }.also { snapshot = it }
    fun emit(action: (T) -> Unit) {
        val listeners = entries()
        NodeLifetime.withOwner(null) {
            for ((id, connection) in listeners) {
                if (connections[id] !== connection) continue
                val listener = connection.listener.get()
                if (listener == null) disconnect(id, connection) else action(listener)
            }
        }
    }
    fun clear() {
        for ((id, connection) in entries()) disconnect(id, connection)
        connections.clear()
        snapshot = null
    }
    fun size(): Int {
        for ((id, connection) in entries()) if (connection.listener.get() == null) disconnect(id, connection)
        return connections.size
    }
}
