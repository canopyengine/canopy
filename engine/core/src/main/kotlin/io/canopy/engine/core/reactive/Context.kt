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
class ContextScopeNode(
    name: String = "__context__",

    /**
     * Context values stored on this scope.
     *
     * Keys are treated as strings by the public API ([provide]/[context]),
     * but the map is kept generic to allow future extension.
     */
    internal val provided: MutableMap<Any, Any?> = linkedMapOf(),

    block: ContextScopeNode.() -> Unit = {},
) : Node<ContextScopeNode>(name, block) {

    /**
     * Adds/overrides context entries for this scope.
     *
     * Values are inherited by all descendants via [Node.context].
     */
    fun provide(vararg entries: Pair<String, Any?>) {
        for ((k, v) in entries) {
            provided[k] = v
        }
    }
}

/**
 * Creates a [ContextScopeNode] under the current DSL parent and executes [block].
 *
 * Note:
 * You typically call this inside a node DSL block. The created context node attaches itself
 * to the current parent via the Node DSL mechanism (it is "invisible" for most lookups).
 */
fun Node<*>.context(block: ContextScopeNode.() -> Unit) {
    ContextScopeNode(block = block)
}

/**
 * Resolves a context value by walking up the parent chain and searching through any
 * [ContextScopeNode] encountered.
 *
 * Lookup rules:
 * - Starts at `this` node and climbs to the root.
 * - For each ancestor that is a [ContextScopeNode], checks if it provides [key].
 * - The closest scope wins (nearest ancestor).
 *
 * @throws IllegalStateException if the key does not exist in any scope.
 */
@Suppress("UNCHECKED_CAST")
fun <T> Node<*>.context(key: String): T {
    var cur: Node<*>? = this
    while (cur != null) {
        if (cur is ContextScopeNode) {
            if (cur.provided.containsKey(key)) return cur.provided[key] as T
        }
        cur = cur.parent
    }
    error("Missing context key '$key' from node $path")
}

/**
 * Nullable variant of [context]. Returns null if the key is missing or if casting fails.
 */
fun <T> Node<*>.contextOrNull(key: String): T? = runCatching { context<T>(key) }.getOrNull()
