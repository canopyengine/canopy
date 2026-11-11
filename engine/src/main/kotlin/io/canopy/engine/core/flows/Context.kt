package io.canopy.engine.core.flows

import kotlin.reflect.KClass
import java.util.*
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
 * This structural scope remains final; configure providers through its builder rather than subclassing it.
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
 * val theme: String = player.getContext<String>(GameContextKey.THEME)
 * val debug: Boolean = player.getContext<String>("debug")
 * ```
 */
class Context(
    name: String = "__context__" + UUID.randomUUID().toString(),
    provided: MutableMap<String, () -> Any?> = linkedMapOf(),
    block: Context.() -> Unit = {},
) : Node<Context>(name, skipOnSearch = true, block) {
    internal var provided by nodeProperty(provided)
    private val typedProviders by nodeProperty(linkedMapOf<KClass<*>, () -> Any?>())

    /**
     * Provides a value under its exact declared type for `core.queries.context<T>()`.
     * The nearest scope with this type wins, even when its provider returns null.
     * Providers run on each read on the game thread; keyed providers remain independent.
     */
    inline fun <reified T : Any> provide(noinline value: () -> T?) = provideTyped(T::class, value)

    @PublishedApi
    internal fun <T : Any> provideTyped(type: KClass<T>, value: () -> T?) {
        typedProviders[type] = value
        sceneManager.hierarchyChanged()
    }

    internal fun hasTypedProvider(type: KClass<*>): Boolean = type in typedProviders

    internal fun typedValue(type: KClass<*>): Any? = typedProviders.getValue(type)()

    /**
     * Provides a value under a raw string key.
     */
    fun <T : Any> provide(key: String, value: () -> T?) {
        provided[key] = value
        sceneManager.hierarchyChanged()
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
@Deprecated("Use getContext/getContextOrNull for immediate access; context with by for live properties")
fun <T : Any> Node<*>.fromContextOrNull(key: String): T? {
    var current: Node<*>? = this

    while (current != null) {
        if (current is Context) {
            val value = current.provided[key]
            if (value != null) {
                return value() as T
            }
        }
        current = current.parent
    }

    return null
}

/**
 * Typed overload of [fromContextOrNull] using a [ContextKey].
 */
@Deprecated("Use getContext/getContextOrNull for immediate access; context with by for live properties")
fun <T : Any> Node<*>.fromContextOrNull(key: ContextKey): T? = fromContextOrNull(key.key)

/**
 * Lazily resolves a context value by raw string key, returning null if not found.
 */
@Deprecated("Use core.queries.context with by for live access; this helper caches its first result")
fun <T : Any> Node<*>.lazyFromContextOrNull(key: String): Lazy<T?> = lazy { fromContextOrNull(key) }

/**
 * Lazily resolves a context value by [ContextKey], returning null if not found.
 */
@Deprecated("Use core.queries.context with by for live access; this helper caches its first result")
fun <T : Any> Node<*>.lazyFromContextOrNull(key: ContextKey): Lazy<T?> = lazy { fromContextOrNull(key) }

/**
 * Resolves a context value by raw string key.
 *
 * @throws NoSuchElementException if the key does not exist in any visible context scope.
 */
@Throws(NoSuchElementException::class)
@Deprecated("Use getContext/getContextOrNull for immediate access; context with by for live properties")
fun <T : Any> Node<*>.fromContext(key: String): T = fromContextOrNull(key)
    ?: throw NoSuchElementException("Context key '$key' not found")

/**
 * Resolves a context value by [ContextKey].
 *
 * @throws NoSuchElementException if the key does not exist in any visible context scope.
 */
@Throws(NoSuchElementException::class)
@Deprecated("Use getContext/getContextOrNull for immediate access; context with by for live properties")
fun <T : Any> Node<*>.fromContext(key: ContextKey): T = fromContext(key.key)

/**
 * Lazily resolves a context value by raw string key.
 *
 * @throws NoSuchElementException if the key does not exist in any visible context scope.
 */
@Throws(NoSuchElementException::class)
@Deprecated("Use core.queries.context with by for live access; this helper caches its first result")
fun <T : Any> Node<*>.lazyFromContext(key: String): Lazy<T> = lazy { fromContext(key) }

/**
 * Lazily resolves a context value by [ContextKey].
 *
 * @throws NoSuchElementException if the key does not exist in any visible context scope.
 */
@Throws(NoSuchElementException::class)
@Deprecated("Use core.queries.context with by for live access; this helper caches its first result")
fun <T : Any> Node<*>.lazyFromContext(key: ContextKey): Lazy<T> = lazy { fromContext(key) }

/** Immediate typed context lookup; nearest provider wins, including null shadowing. */
inline fun <reified T : Any> Node<*>.getContextOrNull(key: String): T? = lookupContext(T::class, key) as T?

/** Immediate context lookup using a named key. */
inline fun <reified T : Any> Node<*>.getContextOrNull(key: ContextKey): T? = getContextOrNull(key.key)

/** Immediate context lookup by exact declared provider type. */
inline fun <reified T : Any> Node<*>.getContextOrNull(): T? = lookupContext(T::class, null) as T?

/** Required immediate lookup; missing or null values throw, incompatible values also fail. */
inline fun <reified T : Any> Node<*>.getContext(key: String): T = getContextOrNull<T>(key)
    ?: throw NoSuchElementException("Context key '$key' not found at $path")

/** Required immediate lookup using a named key. */
inline fun <reified T : Any> Node<*>.getContext(key: ContextKey): T = getContext(key.key)

/** Required immediate lookup by exact declared provider type. */
inline fun <reified T : Any> Node<*>.getContext(): T = getContextOrNull<T>()
    ?: throw NoSuchElementException("Context type '${T::class.qualifiedName}' not found at $path")

@PublishedApi
internal fun Node<*>.lookupContext(type: KClass<*>, key: String?): Any? {
    requireValid("get context")
    observeHierarchy()
    var current: Node<*>? = this
    while (current != null) {
        if (current is Context) {
            val found = if (key == null) current.hasTypedProvider(type) else key in current.provided
            if (found) {
                val value = if (key == null) current.typedValue(type) else current.provided.getValue(key)()
                check(value == null || type.isInstance(value)) {
                    "Context '${key ?: type.qualifiedName}' at $path is not ${type.qualifiedName}"
                }
                return value
            }
        }
        current = current.parent
    }
    return null
}
