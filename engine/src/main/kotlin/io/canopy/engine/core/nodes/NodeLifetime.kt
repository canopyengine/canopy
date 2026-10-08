package io.canopy.engine.core.nodes

/** Internal ownership context shared by lifecycle-aware resources without coupling nodes to their implementations. */
@PublishedApi
internal object NodeLifetime {
    @PublishedApi
    internal val owner = ThreadLocal<Node<*>?>()

    fun current(): Node<*>? = owner.get()

    fun own(cleanup: () -> Unit): () -> Unit = owner.get()?.onRemoval(cleanup) ?: {}

    inline fun <T> withOwner(node: Node<*>?, block: () -> T): T {
        val previous = owner.get()
        owner.set(node)
        try {
            return block()
        } finally {
            owner.set(previous)
        }
    }
}
