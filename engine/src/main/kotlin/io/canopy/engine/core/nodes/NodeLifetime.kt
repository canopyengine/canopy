package io.canopy.engine.core.nodes

/** Internal ownership context shared by lifecycle-aware resources without coupling nodes to their implementations. */
internal object NodeLifetime {
    private val owner = ThreadLocal<Node<*>?>()

    fun own(cleanup: () -> Unit): () -> Unit = owner.get()?.onRemoval(cleanup) ?: {}

    fun <T> withOwner(node: Node<*>?, block: () -> T): T {
        val previous = owner.get()
        owner.set(node)
        try {
            return block()
        } finally {
            owner.set(previous)
        }
    }
}

/** Attempts every teardown operation and preserves the first failure. */
internal class CleanupFailures {
    private var failure: Throwable? = null

    fun attempt(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            val previous = failure
            if (previous == null) {
                failure = error
            } else if (previous !== error) {
                previous.addSuppressed(error)
            }
        }
    }

    fun rethrow() {
        failure?.let { throw it }
    }
}
