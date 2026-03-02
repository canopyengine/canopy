package io.canopy.engine.app.core.screen

import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import ktx.app.KtxScreen

/**
 * Base screen for a Canopy Game
 */
abstract class CanopyScreen : KtxScreen {
    // Used as a way to override the default behavior of ktxScreens
    private var setupCalled = false

    /**
     * Called once when the screen is first shown. Use this method to set up your screen's content, such as creating
     * entities, loading assets, etc.
     */
    open fun setup() {}

    override fun show() {
        // First render is called once
        if (!setupCalled) {
            setup()
            setupCalled = true
            super.show()
            return
        }
        super.show()
    }

    /**
     * Called on each frame - equivalent to an onUpdate
     */
    override fun render(delta: Float) {
        super.render(delta)
        manager<SceneManager>().tick(delta)
    }
}
