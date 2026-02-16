package canopy.core.app

import canopy.core.managers.ManagersRegistry
import canopy.core.nodes.SceneManager
import ktx.app.KtxScreen

abstract class CanopyScreen : KtxScreen {
    private val sceneManager by lazy { ManagersRegistry.get(SceneManager::class) }

    override fun render(delta: Float) {
        super.render(delta)
        sceneManager.tick(delta)
    }
}
