package io.canopy.engine.core.nodes.core

import java.lang.ref.WeakReference

/**
 * A reference to a [Node] that can be resolved later.
 *
 * This is useful when:
 * - you want to point to a node that might not exist yet (path-based lookup)
 * - you want to avoid retaining a node strongly (weak reference)
 *
 * Inspired by Godot's `$"path/to/node"` style lookups.
 *
 * Resolution:
 * - [DirectRef] resolves to a specific node instance (stored as a [WeakReference])
 * - [PathRef] resolves by calling [Node.getNode] on the provided owner
 *
 * Note:
 * - [DirectRef] may become null if the node is garbage-collected.
 * - [PathRef] depends on the node tree structure and may fail if nodes are renamed/moved.
 */
sealed class NodeRef<T : Node<*>> {

    /**
     * Resolves the referenced node relative to [owner].
     *
     * @throws IllegalStateException / IllegalArgumentException if resolution fails
     */
    abstract fun get(owner: Node<*>): T

    /**
     * Direct reference to a node instance using a [WeakReference].
     *
     * This avoids keeping the node alive unintentionally, but resolution may fail
     * if the node has been garbage-collected.
     */
    class DirectRef<T : Node<*>>(node: T) : NodeRef<T>() {
        private val reference = WeakReference(node)

        override fun get(owner: Node<*>): T = reference.get()
            ?: throw IllegalStateException(
                "Direct node reference is no longer valid (node was garbage-collected)."
            )
    }

    /**
     * Path-based reference resolved from an [owner] node.
     *
     * This is flexible (works even if the target node is created later),
     * but it depends on the stability of node paths (names/structure).
     */
    class PathRef<T : Node<T>>(private val path: String) : NodeRef<T>() {
        override fun get(owner: Node<*>): T = owner.getNode(path)
    }

    operator fun <T : Node<T>> NodeRef<T>.invoke(owner: Node<*>): T = get(owner)
}

/**
 * Creates a direct (weak) reference to an existing node instance.
 */
fun <T : Node<*>> nodeRef(node: T): NodeRef<T> = NodeRef.DirectRef(node)

/**
 * Creates a path reference (resolved relative to the owner passed to [NodeRef.get]).
 *
 * Example:
 * ```
 * val weaponRef = nodeRef<Node<*>>("$/Player/Weapon")
 * ```
 */
fun <T : Node<T>> nodeRef(path: String): NodeRef<T> = NodeRef.PathRef(path)
