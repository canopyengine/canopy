package io.canopy.engine.core.flow

import kotlin.jvm.Throws
import io.canopy.engine.core.nodes.Node

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
fun <T : Any> Node<*>.fromContextOrNull(key: String): T? {
    var cur: Node<*>? = this
    while (cur != null) {
        if (cur is Context) {
            val value = cur.provided[key]?.invoke() as? T
            if (value != null) return value
        }
        cur = cur.parent
    }
    return null
}

fun <T : Any> Node<*>.lazyFromContextOrNull(key: String) = lazy { fromContextOrNull<T>(key) }

@Throws(NoSuchElementException::class)
fun <T : Any> Node<*>.fromContext(key: String): T = fromContextOrNull(key)
    ?: throw NoSuchElementException("$key not found")

@Throws(NoSuchElementException::class)
fun <T : Any> Node<*>.lazyFromContext(key: String) = lazy { fromContext<T>(key) }
