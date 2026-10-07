package io.canopy.engine.core.flows.events

import io.canopy.engine.core.nodes.NodeLifetime

/** Retains dependency listeners through their handles without capturing ambient node ownership. */
internal class DependencySubscriptions {
    private var dependencies: Set<Signal<*>> = emptySet()
    private val handlers = mutableMapOf<Signal<*>, EventDisconnectHandler>()

    fun reconcile(next: Set<Signal<*>>, onChange: () -> Unit) {
        val added = next - dependencies
        val removed = dependencies - next
        for (dependency in removed) handlers.remove(dependency)?.disconnect()
        for (dependency in added) {
            handlers[dependency] = NodeLifetime.withOwner(null) { dependency.connect { _ -> onChange() } }
        }
        dependencies = next
    }

    fun clear() {
        handlers.values.forEach { it.disconnect() }
        handlers.clear()
        dependencies = emptySet()
    }
}
