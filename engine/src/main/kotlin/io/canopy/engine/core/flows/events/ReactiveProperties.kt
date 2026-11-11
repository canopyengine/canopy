package io.canopy.engine.core.flows.events

import kotlin.reflect.KProperty
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.NodeProperty

/** Native mutable reactive property. Its descriptor retains a guarded slot, never the source payload. */
class SignalProperty<T> internal constructor(private val slot: NodeProperty<Signal<T>>) {
    /** Reads the source through the receiving Node's guarded engine storage. */
    operator fun getValue(thisRef: Node<*>, property: KProperty<*>): T {
        val source = slot.getValue(thisRef, property)
        return if (thisRef.isInsideTree || !thisRef.hasEnteredTree) source() else untrack { source() }
    }

    /** Assigns using the existing equality and notification rules on the game thread. */
    operator fun setValue(thisRef: Node<*>, property: KProperty<*>, value: T) {
        slot.getValue(thisRef, property).update { value }
    }
}

/** Native read-only derived property with a guarded engine-owned source. */
class ComputedProperty<T> internal constructor(private val slot: NodeProperty<Computed<T>>) {
    /** Reads the cached calculation while entered, or evaluates without observation while detached. */
    operator fun getValue(thisRef: Node<*>, property: KProperty<*>): T {
        val source = slot.getValue(thisRef, property)
        return if (thisRef.isInsideTree || !thisRef.hasEnteredTree) source() else untrack { source() }
    }
}
