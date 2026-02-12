package canopy.core.nodes.core

sealed class NodeRef<T : Node<*>> {
    abstract fun get(owner: Node<*>): T

    class DirectRef<T : Node<*>>(
        private val node: T,
    ) : NodeRef<T>() {
        override fun get(owner: Node<*>): T = node
    }

    class PathRef<T : Node<T>>(
        private val path: String,
    ) : NodeRef<T>() {
        override fun get(owner: Node<*>): T = owner.getNode(path)
    }
}

fun <T : Node<*>> nodeRef(node: T): NodeRef<T> = NodeRef.DirectRef(node) as NodeRef<T>

fun <T : Node<T>> nodeRef(path: String): NodeRef<T> = NodeRef.PathRef(path)
