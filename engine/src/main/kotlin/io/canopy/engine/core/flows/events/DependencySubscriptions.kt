package io.canopy.engine.core.flows.events

import io.canopy.engine.core.nodes.NodeLifetime

/** Retains dependency listeners through their handles without capturing ambient node ownership. */
internal class DependencySubscriptions {
    private var dependencies: Set<Signal<*>> = emptySet()
    private val handlers = mutableMapOf<Signal<*>, EventDisconnectHandler>()

    fun reconcile(next: Set<Signal<*>>, onChange: () -> Unit) = reconcile(next, onChange, null)

    fun reconcile(next: Set<Signal<*>>, onChange: () -> Unit, onDisconnect: (() -> Unit)?) {
        val added = next - dependencies
        val removed = dependencies - next
        for (dependency in removed) {
            handlers.remove(dependency)?.let { handle ->
                handle.onDisconnected = null
                handle.disconnect()
            }
        }
        for (dependency in added) {
            val handle = NodeLifetime.withOwner(null) { dependency.connect { _ -> onChange() } }
            handlers[dependency] = handle
            if (onDisconnect != null) {
                handle.onDisconnected = {
                    if (handlers[dependency] === handle) {
                        handlers.remove(dependency)
                        dependencies = dependencies - dependency
                        onDisconnect()
                    }
                }
            }
        }
        dependencies = next
    }

    fun clear() {
        val previous = handlers.values.toList()
        handlers.clear()
        dependencies = emptySet()
        previous.forEach {
            it.onDisconnected = null
            it.disconnect()
        }
    }
}
