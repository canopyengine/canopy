package io.canopy.engine.core

/** Attempts every teardown operation and preserves the first failure. */
internal class CleanupFailures(initialFailure: Throwable? = null) {
    var failure: Throwable? = initialFailure
        private set

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
