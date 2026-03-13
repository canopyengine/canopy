package io.canopy.engine.core.flow.contexts

/**
 * Wrapper on ``resolve`` methods, so that code is more readable
 */
data class ContextResolver(private val contexts: List<Context>) {
    /**
     * Resolves a context value by walking up the parent chain and searching through any
     * [Context] encountered.
     *
     * Lookup rules:
     * - Starts at `this` node and climbs to the root.
     * - For each ancestor that is a [Context], checks if it provides [key].
     * - The closest scope wins (nearest ancestor).
     *
     * @throws IllegalStateException if the key does not exist in any scope.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resolve(key: String): T {
        contexts.forEach {
            return it.provided[key]?.invoke() as? T ?: return@forEach
        }
        error("Missing context key '$key'")
    }

    fun <T : Any> lazyResolve(key: String) = lazy { resolve<T>(key) }

    /**
     * Fetches value from [Context]s, or null if no value is found
     */
    fun <T : Any> resolveOrNull(key: String): T? = runCatching { resolve<T>(key) }.getOrNull()

    fun <T : Any> lazyResolveOrNull(key: String) = lazy { resolveOrNull<T>(key) }
}
