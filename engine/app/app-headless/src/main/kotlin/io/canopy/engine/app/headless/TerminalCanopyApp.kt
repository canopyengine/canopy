package io.canopy.engine.app.headless

import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import io.canopy.engine.app.core.CanopyApp
import io.canopy.engine.app.core.CanopyAppConfig
import io.canopy.engine.app.core.CanopyAppHandle
import io.canopy.engine.core.managers.SceneManager

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
