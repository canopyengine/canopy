package io.canopy.engine.core.queries

import io.canopy.engine.core.managers.Manager

/**
 * Global dependency on the current assignable manager registration, independent of receiver lifetime.
 * Each game-thread read resolves again; missing values throw. core.managers.manager performs an immediate lookup.
 */
inline fun <reified T : Manager> manager(): GlobalDependency<T> = GlobalDependency.create(T::class)

/** Optional version of [manager]; missing dependencies return null on each read. */
inline fun <reified T : Manager> managerOrNull(): GlobalDependency<T?> =
    GlobalDependency.create(T::class, optional = true)
