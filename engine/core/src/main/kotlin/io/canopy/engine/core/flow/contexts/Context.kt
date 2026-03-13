package io.canopy.engine.core.flow.contexts

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
 * Backtracks the tree and returns a [ContextResolver] - users use this to resolve data
 */
fun Node<*>.getContext(): ContextResolver {
    val contexts = mutableListOf<Context>()

    var cur: Node<*>? = this
    while (cur != null) {
        if (cur is Context) contexts += (cur)
        cur = cur.parent
    }

    return ContextResolver(contexts)
}
