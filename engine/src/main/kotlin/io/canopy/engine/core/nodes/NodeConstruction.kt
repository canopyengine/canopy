package io.canopy.engine.core.nodes

/**
 * Constructs nodes within a game-thread-confined rollback boundary. The block must not suspend or switch threads.
 *
 * A failure permanently destroys every node created by [block], releases registered ownership and removes
 * tree membership before rethrowing the original failure. Cleanup failures are suppressed on that failure.
 * Successful nested boundaries join their enclosing boundary, including nonlocal returns; a failed nested
 * boundary affects only its own new nodes. Existing nodes and arbitrary external mutations are not transactional.
 *
 * Java callers and builds without the Canopy compiler plugin must wrap potentially failing constructors
 * explicitly. The base constructor cannot catch a subclass initializer failure after it returns.
 */
inline fun <T> nodeConstruction(block: () -> T): T {
    val transaction = NodeConstruction.begin()
    var failure: Throwable? = null
    try {
        return block()
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        NodeConstruction.complete(transaction, failure)
    }
}

@PublishedApi
internal object NodeConstruction {
    @PublishedApi
    internal class Transaction(val previous: Transaction?) {
        val nodes = linkedSetOf<Node<*>>()
        val pending = ArrayDeque<Node<*>>()

        fun add(node: Node<*>) {
            if (nodes.add(node)) pending.addLast(node)
        }
    }

    private val current = ThreadLocal<Transaction?>()

    fun record(node: Node<*>) {
        current.get()?.add(node)
    }

    @PublishedApi
    internal fun begin(): Transaction = Transaction(current.get()).also { current.set(it) }

    @PublishedApi
    internal fun complete(transaction: Transaction, failure: Throwable?) {
        try {
            if (failure == null) {
                transaction.previous?.let { previous -> transaction.nodes.forEach(previous::add) }
                return
            }
            // Keep the scope installed while cleanup runs: nodes created by cleanup also belong to rollback.
            while (transaction.pending.isNotEmpty()) {
                val node = transaction.pending.removeFirst()
                if (node.isFreed) continue
                try {
                    node.owningManager().rollbackConstruction(node, transaction.nodes)
                } catch (cleanup: Throwable) {
                    if (cleanup !== failure) failure.addSuppressed(cleanup)
                }
            }
        } finally {
            current.set(transaction.previous)
        }
    }
}
