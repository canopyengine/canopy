package io.canopy.engine.app.core

import io.canopy.engine.core.managers.InjectionManager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import ktx.app.KtxScreen

abstract class CanopyScreen : KtxScreen {
    protected val sceneManager by lazy { ManagersRegistry.get(SceneManager::class) }
    protected val injectionManager by lazy { ManagersRegistry.get(InjectionManager::class) }

    /**
     * Called once when the screen is first shown. Use this method to set up your screen's content, such as creating
     * entities, loading assets, etc.
     */
    open fun setup() {}

    override fun render(delta: Float) {
        super.render(delta)
        sceneManager.tick(delta)
    }
}
