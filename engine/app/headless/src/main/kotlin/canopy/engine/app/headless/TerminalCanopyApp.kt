package canopy.engine.app.headless

import canopy.engine.app.appkit.CanopyApp
import canopy.engine.app.appkit.CanopyAppConfig
import canopy.engine.app.appkit.CanopyAppHandle
import canopy.engine.core.managers.SceneManager
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration

class TerminalCanopyApp(
    sceneManager: SceneManager = SceneManager(),
    config: CanopyAppConfig,
    onCreate: (CanopyApp<CanopyAppConfig>) -> Unit = {},
    onResize: (CanopyApp<CanopyAppConfig>, width: Int, height: Int) -> Unit = { _, _, _ -> },
    onDispose: (CanopyApp<CanopyAppConfig>) -> Unit = {},
) : CanopyApp<CanopyAppConfig>(
    sceneManager,
    config,
    onCreate,
    onResize,
    onDispose
) {
    override fun defaultConfig(): CanopyAppConfig = CanopyAppConfig(
        title = "Test Headless Canopy Game"
    )

    override fun internalLaunch(config: CanopyAppConfig, vararg args: String): CanopyAppHandle {
        val headless = HeadlessApplication(this, HeadlessApplicationConfiguration())

        return CanopyAppHandle {
            headless.exit()
        }
    }
}
