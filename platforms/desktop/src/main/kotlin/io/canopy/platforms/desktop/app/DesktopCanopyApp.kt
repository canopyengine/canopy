package io.canopy.platforms.desktop.app

import com.badlogic.gdx.Gdx
import io.canopy.adapters.libgdx.data.assets.LibGdxAssetsManager
import io.canopy.engine.app.App
import io.canopy.engine.app.desktop.DesktopCanopyAppConfig
import io.canopy.engine.app.desktop.StartupHelper
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.logging.CanopyLogs
import io.canopy.engine.utils.UnstableApi
import io.canopy.platforms.desktop.graphics.managers.CameraManager
import io.canopy.platforms.desktop.graphics.systems.RenderSystem

/**
 * LWJGL3 Implementation of [CanopyApp]
 */
@UnstableApi
class DesktopCanopyApp internal constructor() : App<DesktopCanopyAppConfig>() {
    private val log = CanopyLogs.get("canopy.app.desktop")

    override fun ready() {
        installBackendHandle(
            requestExit = { Gdx.app.postRunnable { Gdx.app.exit() } },
            forceClose = { Runtime.getRuntime().halt(0) } // last resort
        )

        sceneManager.apply {
            +RenderSystem(config.screenWidth, config.screenHeight)
        }

        ManagersRegistry.apply {
            +CameraManager()
            +LibGdxAssetsManager()
        }

        super.create()
    }

    override fun defaultConfig(): DesktopCanopyAppConfig =
        _root_ide_package_.io.canopy.engine.app.desktop.DesktopCanopyAppConfig()

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
fun desktopApp(
    builder: DesktopCanopyApp.() -> Unit = {},
): DesktopCanopyApp = DesktopCanopyApp()
    .apply(builder)
