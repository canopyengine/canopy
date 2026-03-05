package io.canopy.engine.core.reactive

import io.canopy.engine.core.nodes.core.Node

/**
 * A transparent "scope" node used to attach contextual values to a subtree.
 *
 * Context scopes are implementation details of the DSL and are typically treated as invisible:
 * - [Node.getNode] path resolution intentionally skips these nodes so you don't have to include
 *   "__context__" segments in paths.
 *
 * Use cases:
 * - Provide shared configuration to a subtree (theme, tags, services)
 * - Avoid threading values through constructors
 *
 * Example:
 * ```
 * root.context {
 *   provide("theme" to "dark", "difficulty" to 3)
 *
 *   +PlayerNode { ... }
 * }
 *
 * val theme: String = player.context("theme")
 * ```
 */
class Context(
    name: String = "__context__",
    internal val provided: MutableMap<String, () -> Any?> = linkedMapOf(),
    block: Context.() -> Unit = {},
) : Node<Context>(name, block) {

    fun <T : Any> provide(key: String, value: () -> T?) {
        provided[key] = value
    }
}

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
fun <T : Any> Node<*>.resolve(key: String): T {
    var cur: Node<*>? = this
    while (cur != null) {
        if (cur is Context && cur.provided.containsKey(key)) {
            val callback = cur.provided[key] ?: error("$key not found in context")
            return callback() as T // may be null -> will throw if T is non-null; that’s fine
        }
        cur = cur.parent
    }
    error("Missing context key '$key' from node $path")
}

fun <T : Any> Node<*>.lazyResolve(key: String) = lazy { resolve<T>(key) }

/**
 * Fetches value from [Context]s, or null if no value is found
 */
fun <T : Any> Node<*>.resolveOrNull(key: String): T? = runCatching { resolve<T>(key) }.getOrNull()

fun <T : Any> Node<*>.lazyResolveOrNull(key: String) = lazy { resolveOrNull<T>(key) }
