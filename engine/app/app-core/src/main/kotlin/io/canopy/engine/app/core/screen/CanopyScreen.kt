package io.canopy.engine.app.core.screen

import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import ktx.app.KtxScreen

/**
 * Base screen implementation for Canopy applications.
 *
 * This class extends [KtxScreen] and adds a small lifecycle convenience:
 * a [setup] method that is guaranteed to run **only once**, the first time
 * the screen becomes visible.
 *
 * This avoids common issues where `show()` may be called multiple times
 * during a screen's lifetime.
 *
 * Typical usage:
 *
 * ```
 * class MainMenuScreen : CanopyScreen() {
 *     override fun setup() {
 *         // Create UI, entities, etc.
 *     }
 * }
 * ```
 *
 * Scene integration:
 * Each frame the global [SceneManager] is ticked automatically,
 * allowing scene logic and systems to update without requiring
 * explicit calls in every screen.
 */
abstract class CanopyScreen : KtxScreen {

    /**
     * Tracks whether [setup] has already been executed.
     */
    private var setupCalled = false

    /**
     * Called once when the screen is first shown.
     *
     * Use this method to initialize screen content such as:
     * - creating entities
     * - registering systems
     * - loading assets
     * - building UI
     */
    open fun setup() {}

    /**
     * Called by LibGDX/KTX when the screen becomes active.
     *
     * We intercept this call to ensure [setup] executes only once.
     */
    override fun show() {
        if (!setupCalled) {
            setup()
            setupCalled = true
        }

        super.show()
    }

    /**
     * Called every frame.
     *
     * Delegates to the [SceneManager] so that scene systems and entities
     * are updated automatically for the active screen.
     */
    override fun render(delta: Float) {
        super.render(delta)
        manager<SceneManager>().tick(delta)
    }
}
