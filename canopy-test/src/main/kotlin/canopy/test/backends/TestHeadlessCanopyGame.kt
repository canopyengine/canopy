package canopy.test.backends

import canopy.app.CanopyGame
import canopy.app.backends.CanopyBackendConfig
import canopy.core.managers.InjectionManager
import canopy.core.managers.ManagersRegistry
import canopy.core.managers.SceneManager

class TestHeadlessCanopyGame(
    sceneManager: SceneManager = SceneManager(),
    onCreate: (CanopyGame<CanopyBackendConfig>) -> Unit = {},
    onResize: (CanopyGame<CanopyBackendConfig>, width: Int, height: Int) -> Unit = { _, _, _ -> },
    onDispose: (CanopyGame<CanopyBackendConfig>) -> Unit = {},
) : CanopyGame<CanopyBackendConfig>(
    sceneManager,
    TestHeadlessBackend,
    onCreate,
    onResize,
    onDispose
) {
    override fun create() {
        ManagersRegistry
            .apply {
                register(InjectionManager())
                register(sceneManager)
                setup()
            }
        onCreate(this)
    }

    override fun resize(width: Int, height: Int) {
        onResize(this, width, height)
    }

    override fun render() {}

    override fun pause() {}

    override fun resume() {}

    override fun dispose() {
        onDispose(this)
    }

    override fun defaultConfig(): CanopyBackendConfig = CanopyBackendConfig(
        width = 800,
        height = 600,
        title = "Test Headless Canopy Game"
    )
}
