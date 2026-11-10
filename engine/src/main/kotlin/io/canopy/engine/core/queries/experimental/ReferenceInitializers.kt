package io.canopy.engine.core.queries.experimental

import kotlin.reflect.KClass
import io.canopy.engine.core.nodes.Node

/** Experimental compiler-backed reference syntax; not part of the stable lookup API. */
@RequiresOptIn("Reference initializers are a prototype; use the stable delegated API in production.")
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalReferenceInitializers

/**
 * Declares a live ancestor reference as a direct val initializer on a Node subclass.
 * The compiler replaces the initializer with an on-access lookup. Construction performs no lookup.
 * Requires the matching compiler plugin. Existing delegated queries remain unchanged.
 */
@ExperimentalReferenceInitializers
inline fun <reified T : Node<*>> ancestor(): T =
    error("Use ancestor<T>() directly as a Node val initializer with the matching Canopy compiler plugin")

/** Compiler ABI for the isolated prototype. Resolves the actual hierarchy on the game thread. */
@PublishedApi
internal fun <T : Node<*>> resolveAncestor(owner: Node<*>, type: KClass<T>, property: String): T {
    owner.requireValid("read reference '$property'")
    var current = owner.parent
    while (current != null) {
        if (type.isInstance(current)) {
            @Suppress("UNCHECKED_CAST")
            return current as T
        }
        current = current.parent
    }
    throw NoSuchElementException("Reference '$property': ancestor<${type.qualifiedName}> not found for ${owner.path}")
}
