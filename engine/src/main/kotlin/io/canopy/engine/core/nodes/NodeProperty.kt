package io.canopy.engine.core.nodes

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Guarded storage owned by engine state. Only [Node.nodeProperty] can construct this final delegate. */
class NodeProperty<T> private constructor(private val key: Any) : ReadWriteProperty<Node<*>, T> {
    internal companion object {
        fun <T> create(key: Any): NodeProperty<T> = NodeProperty(key)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Node<*>, property: KProperty<*>): T =
        thisRef.propertyState(property, false).properties[key] as T
    override fun setValue(thisRef: Node<*>, property: KProperty<*>, value: T) {
        thisRef.propertyState(property, true).properties[key] = value
    }
}
