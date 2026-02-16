package canopy.backends.test

import canopy.core.backend.CanopyBackendConfig
import canopy.core.app.CanopyGame
import canopy.core.managers.InjectionManager
import canopy.core.managers.ManagersRegistry
import canopy.core.nodes.SceneManager

class TestHeadlessCanopyGame(
    sceneManager: SceneManager = SceneManager(),
    onCreate: (SceneManager) -> Unit = {},
    onResize: (SceneManager, width: Int, height: Int) -> Unit = { _, _, _ -> },
    onDispose: (SceneManager) -> Unit = {},
) : CanopyGame<CanopyBackendConfig>(
        sceneManager,
        TestHeadlessBackend,
        onCreate,
        onResize,
        onDispose,
    ) {

    override fun create() {
        ManagersRegistry.apply {
            register(sceneManager)
            register(InjectionManager())
        }.setup()
        onCreate(sceneManager)
    }

    override fun resize(
        width: Int,
        height: Int,
    ) {
        onResize(sceneManager, width, height)
    }

    override fun render() {}

    override fun pause() {}

    override fun resume() {}

    override fun dispose() {onDispose(sceneManager)}

    override fun defaultConfig(): CanopyBackendConfig =
        CanopyBackendConfig(
            width = 800,
            height = 600,
            title = "Test Headless Canopy Game",
        )
}
