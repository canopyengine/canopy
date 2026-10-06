package io.canopy.engine.core.queries

import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Shared immutable metadata and missing-value policy for engine-controlled runtime dependencies.
 *
 * [GlobalDependency] resolves application services independently of receivers; [NodeDependency] resolves a node's
 * hierarchy and context. Only concrete types support property delegation, so node lookups retain receiver safety.
 * Each read resolves again on the game thread. No dependency retains owners, providers or resolved values.
 * Required missing values throw [NoSuchElementException]; optional reads return null only for absence.
 * Resolution failures propagate. Construct dependencies through the factories in this package.
 */
sealed class Dependency<T> protected constructor(
    private val lookup: String,
    protected val type: KClass<*>,
    protected val key: String?,
    private val optional: Boolean,
) {
    @Suppress("UNCHECKED_CAST")
    internal fun finish(result: Any?, property: KProperty<*>, scope: () -> String): T {
        if (result == null && !optional) {
            throw NoSuchElementException(
                "Dependency '${property.name}': $lookup<${type.qualifiedName}>" +
                    (key?.let { "('$it')" } ?: "") + " not found for " + scope()
            )
        }
        return result as T
    }
}
