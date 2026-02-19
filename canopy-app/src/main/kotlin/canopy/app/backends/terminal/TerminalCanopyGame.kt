package canopy.app.backends.terminal

import canopy.app.game.CanopyGame
import canopy.app.game.CanopyGameConfig
import canopy.app.game.CanopyGameHandle
import canopy.core.managers.SceneManager
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration

class TerminalCanopyGame(
    sceneManager: SceneManager = SceneManager(),
    config: CanopyGameConfig,
    onCreate: (CanopyGame<CanopyGameConfig>) -> Unit = {},
    onResize: (CanopyGame<CanopyGameConfig>, width: Int, height: Int) -> Unit = { _, _, _ -> },
    onDispose: (CanopyGame<CanopyGameConfig>) -> Unit = {},
) : CanopyGame<CanopyGameConfig>(
    sceneManager,
    config,
    onCreate,
    onResize,
    onDispose
) {
    override fun defaultConfig(): CanopyGameConfig = CanopyGameConfig(
        title = "Test Headless Canopy Game"
    )

    override fun internalLaunch(config: CanopyGameConfig, vararg args: String): CanopyGameHandle {
        val headless = HeadlessApplication(this, HeadlessApplicationConfiguration())

        return CanopyGameHandle {
            headless.exit()
        }
    }
}
