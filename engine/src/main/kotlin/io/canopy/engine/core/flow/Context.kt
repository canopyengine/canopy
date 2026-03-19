package io.canopy.engine.core.flow

import kotlin.jvm.Throws
import io.canopy.engine.core.nodes.Node

/**
 * Represents a typed key used to store and resolve values from a [Context].
 *
 * Users are encouraged to implement this interface with their own enums instead of
 * using raw strings, as it improves readability, refactor safety, and discoverability.
 *
 * Example:
 * ```
 * enum class GameContextKey(override val key: String) : ContextKey {
 *     THEME("theme"),
 *     DIFFICULTY("difficulty")
 * }
 * ```
 */
interface ContextKey {
    val key: String
}

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
 * enum class GameContextKey(override val key: String) : ContextKey {
 *     THEME("theme"),
 *     DIFFICULTY("difficulty")
 * }
 *
 * root.context {
 *     provide(GameContextKey.THEME) { "dark" }
 *     provide("debug") { true }
 *
 *     +PlayerNode { ... }
 * }
 *
 * val theme: String = player.fromContext(GameContextKey.THEME)
 * val debug: Boolean = player.fromContext("debug")
 * ```
 */
class Context(
    name: String = "__context__",
    internal val provided: MutableMap<String, () -> Any?> = linkedMapOf(),
    block: Context.() -> Unit = {},
) : Node<Context>(name, block) {

    /**
     * Provides a value under a raw string key.
     */
    fun <T : Any> provide(key: String, value: () -> T?) {
        provided[key] = value
    }

    /**
     * Provides a value under a typed [ContextKey].
     */
    fun <T : Any> provide(key: ContextKey, value: () -> T?) {
        provide(key.key, value)
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
 * Returns null if no matching key is found.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> Node<*>.fromContextOrNull(key: String): T? {
    var current: Node<*>? = this

    while (current != null) {
        if (current is Context) {
            val provider = current.provided[key]
            if (provider != null) {
                val value = provider.invoke() as? T
                if (value != null) return value
            }
        }
        current = current.parent
    }

    return null
}

/**
 * Typed overload of [fromContextOrNull] using a [ContextKey].
 */
fun <T : Any> Node<*>.fromContextOrNull(key: ContextKey): T? = fromContextOrNull(key.key)

/**
 * Lazily resolves a context value by raw string key, returning null if not found.
 */
fun <T : Any> Node<*>.lazyFromContextOrNull(key: String): Lazy<T?> = lazy { fromContextOrNull<T>(key) }

/**
 * Lazily resolves a context value by [ContextKey], returning null if not found.
 */
fun <T : Any> Node<*>.lazyFromContextOrNull(key: ContextKey): Lazy<T?> = lazy { fromContextOrNull<T>(key) }

/**
 * Resolves a context value by raw string key.
 *
 * @throws NoSuchElementException if the key does not exist in any visible context scope.
 */
@Throws(NoSuchElementException::class)
fun <T : Any> Node<*>.fromContext(key: String): T = fromContextOrNull<T>(key)
    ?: throw NoSuchElementException("Context key '$key' not found")

/**
 * Resolves a context value by [ContextKey].
 *
 * @throws NoSuchElementException if the key does not exist in any visible context scope.
 */
@Throws(NoSuchElementException::class)
fun <T : Any> Node<*>.fromContext(key: ContextKey): T = fromContext(key.key)

/**
 * Lazily resolves a context value by raw string key.
 *
 * @throws NoSuchElementException if the key does not exist in any visible context scope.
 */
@Throws(NoSuchElementException::class)
fun <T : Any> Node<*>.lazyFromContext(key: String): Lazy<T> = lazy { fromContext<T>(key) }

/**
 * Lazily resolves a context value by [ContextKey].
 *
 * @throws NoSuchElementException if the key does not exist in any visible context scope.
 */
@Throws(NoSuchElementException::class)
fun <T : Any> Node<*>.lazyFromContext(key: ContextKey): Lazy<T> = lazy { fromContext<T>(key) }
