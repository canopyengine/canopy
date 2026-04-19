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
 * 1. [onEnter] is called during application startup.
 * 2. [onExit] is called when the application shuts down.
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
    fun onEnter() = Unit

    /**
     * Called on each app frame
     */
    fun onUpdate(delta: Float) = Unit

    /**
     * Called on screen resize
     */
    fun onResize(width: Int, height: Int) = Unit

    /**
     * Called when the manager is being shut down.
     *
     * Use this method to release resources or reset internal state.
     */
    fun onExit() = Unit
}
