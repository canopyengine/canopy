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
import ktx.async.KtxAsync

@UnstableApi
class DesktopCanopyApp internal constructor() : CanopyApp<DesktopCanopyAppConfig>() {
    // App/variant logger (not engine.*)
    private val log = Logs.get("canopy.app.desktop")

    override fun create() {
        // install the backend exit behavior as soon as libGDX is alive
        installBackendHandle(
            requestExit = { Gdx.app.postRunnable { Gdx.app.exit() } },
            forceClose = { Runtime.getRuntime().halt(0) } // last resort
        )

        // your existing setup
        KtxAsync.initiate()
        sceneManager.registerSystem(
            RenderSystem(config.screenWidth, config.screenHeight)
        )
        ManagersRegistry.register(CameraManager())
        ManagersRegistry.register(AssetsManager())

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
fun desktopApp(builder: DesktopCanopyApp.() -> Unit): DesktopCanopyApp = DesktopCanopyApp().apply(builder)
