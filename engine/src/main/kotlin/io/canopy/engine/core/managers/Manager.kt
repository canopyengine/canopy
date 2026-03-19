package io.canopy.engine.core.managers

/**
 * Base interface for engine managers.
 *
 * Managers represent global services responsible for a specific subsystem
 * (e.g. scenes, assets, dependency injection).
 *
 * Managers are typically registered through [ManagersRegistry] and follow
 * a simple lifecycle:
 *
 * 1. [setup] is called during application startup.
 * 2. [teardown] is called when the application shuts down.
 *
 * Implementations may override these methods to allocate or release
 * resources as needed.
 */
interface Manager {

    /**
     * Called when the manager is initialized during application startup.
     *
     * Use this method to allocate resources, register services,
     * or perform any initialization logic.
     */
    fun setup() = Unit

    /**
     * Called when the manager is being shut down.
     *
     * Use this method to release resources or reset internal state.
     */
    fun teardown() = Unit
}
