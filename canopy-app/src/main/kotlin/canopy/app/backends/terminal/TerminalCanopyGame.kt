package canopy.app.backends.terminal

import canopy.app.CanopyGame
import canopy.app.backends.CanopyBackendConfig
import canopy.core.managers.SceneManager

class TerminalCanopyGame(
    sceneManager: SceneManager = SceneManager(),
    onCreate: (CanopyGame<CanopyBackendConfig>) -> Unit = {},
    onResize: (CanopyGame<CanopyBackendConfig>, width: Int, height: Int) -> Unit = { _, _, _ -> },
    onDispose: (CanopyGame<CanopyBackendConfig>) -> Unit = {},
) : CanopyGame<CanopyBackendConfig>(
    sceneManager,
    TerminalCanopyBackend,
    onCreate,
    onResize,
    onDispose
) {
    override fun defaultConfig(): CanopyBackendConfig = CanopyBackendConfig(
        width = 800,
        height = 600,
        title = "Test Headless Canopy Game"
    )
}
