package io.canopy.engine.app.core.screen

import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.lazyManager
import ktx.app.KtxScreen

abstract class CanopyScreen : KtxScreen {
    protected val sceneManager by lazyManager<SceneManager>()

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
            return
        }
        super.show()
    }

    override fun render(delta: Float) {
        super.render(delta)
        sceneManager.tick(delta)
    }
}
