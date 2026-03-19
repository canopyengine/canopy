package io.canopy.engine.app.screen

import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager

/**
 * Base screen abstraction for Canopy applications.
 *
 * This class is platform-agnostic and defines a simple lifecycle:
 *
 * - [onEnter] → called when the screen becomes active
 * - [onFrame] → called every frame
 * - [onResize] → called when the surface changes size
 * - [onExit] → called when the screen is no longer active
 * - [dispose] → called when the screen is destroyed
 *
 * Additionally, [setup] is guaranteed to run only once,
 * the first time the screen is entered.
 */
abstract class Screen {

    private var setupCalled = false

    /**
     * Called once when the screen is first entered.
     */
    open fun setup() {}

    /**
     * Called when the screen becomes active.
     */
    open fun onEnter() {
        if (!setupCalled) {
            setup()
            setupCalled = true
        }
    }

    /**
     * Called every frame.
     *
     * @param delta Time since last frame (in seconds)
     */
    open fun onFrame(delta: Float) {
        manager<SceneManager>().tick(delta)
    }

    /**
     * Called when the screen is resized.
     */
    open fun onResize(width: Int, height: Int) {}

    /**
     * Called when the screen is no longer active.
     */
    open fun onExit() {}

    /**
     * Called when the screen is destroyed.
     */
    open fun dispose() {}
}
