package io.canopy.engine.app.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import io.canopy.engine.app.core.CanopyApp
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.data.core.assets.AssetsManager
import io.canopy.engine.graphics.managers.CameraManager
import io.canopy.engine.graphics.systems.RenderSystem
import io.canopy.engine.logging.api.Logs
import io.canopy.engine.utils.UnstableApi

/**
 * LWJGL3 Implementation of [CanopyApp]
 */
@UnstableApi
class DesktopCanopyApp internal constructor() : CanopyApp<DesktopCanopyAppConfig>() {
    private val log = Logs.get("canopy.app.desktop")

    override fun create() {
        installBackendHandle(
            requestExit = { Gdx.app.postRunnable { Gdx.app.exit() } },
            forceClose = { Runtime.getRuntime().halt(0) } // last resort
        )

        sceneManager.apply {
            +RenderSystem(config.screenWidth, config.screenHeight)
        }

        ManagersRegistry.apply {
            +CameraManager()
            +AssetsManager()
        }

        super.create()
    }

    override fun defaultConfig(): DesktopCanopyAppConfig = DesktopCanopyAppConfig()

    override fun internalLaunch(config: DesktopCanopyAppConfig, vararg args: String) {
        if (StartupHelper.startNewJvmIfRequired()) return

        val cfg = Lwjgl3ApplicationConfiguration().apply {
            setTitle(config.title)
            useVsync(true)
            setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1)
            setWindowedMode(config.screenWidth, config.screenHeight)
            setWindowIcon(*config.icons.toTypedArray())
            config.configure(this)
        }

        Lwjgl3Application(this, cfg) // blocks until exit
    }

    /* Helper methods */
}

@UnstableApi
fun desktopApp(builder: DesktopCanopyApp.() -> Unit = {}): DesktopCanopyApp = DesktopCanopyApp().apply(builder)
