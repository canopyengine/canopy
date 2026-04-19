package io.canopy.engine.app

/**
 * Base screen abstraction for Canopy applications.
 *
 * This class is platform-agnostic and defines a simple lifecycle:
 *
 * - [onActive] → called when the screen becomes active
 * - [onUpdate] → called every frame
 * - [onResize] → called when the surface changes size
 * - [onExit] → called when the screen is no longer active
 * - [dispose] → called when the screen is destroyed
 *
 * Additionally, [onEnter] is guaranteed to run only once,
 * the first time the screen is entered.
 */
abstract class Screen {

    private var setupCalled = false

    /**
     * Called once when the screen is first entered.
     */
    open fun onEnter() {}

    /**
     * Called when the screen becomes active.
     */
    open fun onActive() {}

    /**
     * Called when the screen is no longer active.
     */
    open fun onInactive() {}

    /**
     * Called every frame.
     *
     * @param delta Time since last frame (in seconds)
     */
    open fun onUpdate(delta: Float) {}

    /**
     * Called when the screen is resized.
     */
    open fun onResize(width: Int, height: Int) {}

    /**
     * Called when the screen is destroyed.
     */
    open fun onExit() {}
}
