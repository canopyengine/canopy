package canopy.backends.terminal

import canopy.core.app.CanopyGame
import canopy.core.backend.CanopyBackendConfig
import canopy.core.nodes.SceneManager

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
        onDispose,
    ) {
    override fun defaultConfig(): CanopyBackendConfig =
        CanopyBackendConfig(
            width = 800,
            height = 600,
            title = "Test Headless Canopy Game",
        )
}
