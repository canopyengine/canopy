package io.canopy.engine.core.nodes.core

import java.lang.ref.WeakReference

/**
 * Represents a reference to a node.
 * This allows for lazily reference a node which may not exist, or by its path.
 * Similar to "$<node path>" in Godot
 */
sealed class NodeRef<T : Node<*>> {
    abstract fun get(owner: Node<*>): T

    class DirectRef<T : Node<*>>(node: T) : NodeRef<T>() {
        private val reference = WeakReference(node)
        override fun get(owner: Node<*>): T = reference.get()
            ?: throw IllegalStateException("Your node has no reference")
    }

    class PathRef<T : Node<T>>(private val path: String) : NodeRef<T>() {
        override fun get(owner: Node<*>): T = owner.getNode(path)
    }
}

fun <T : Node<*>> nodeRef(node: T): NodeRef<T> = NodeRef.DirectRef(node) as NodeRef<T>

fun <T : Node<T>> nodeRef(path: String): NodeRef<T> = NodeRef.PathRef(path)
