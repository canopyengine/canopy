package io.canopy.engine.core.queries

import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry

/**
 * Read-only global manager dependency, resolved on each game-thread read regardless of the receiver's lifecycle.
 * Supports ordinary objects, top-level/local properties, nodes and behaviors without nodes. Retains only lookup
 * metadata; registry replacement is observed on the next read. Construct through [manager] or [managerOrNull].
 */
class GlobalDependency<T> private constructor(type: KClass<out Manager>, optional: Boolean) :
    Dependency<T>("Manager", type, null, optional) {
    @PublishedApi
    internal companion object {
        fun <T> create(type: KClass<out Manager>, optional: Boolean = false): GlobalDependency<T> =
            GlobalDependency(type, optional)
    }

    /** Resolves from the global registry without accessing or retaining [thisRef]. */
    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
        finish(ManagersRegistry.getManagerOrNull(type as KClass<Manager>), property) { "the global manager registry" }
}
