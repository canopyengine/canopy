package io.canopy.platforms.desktop.app

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import io.canopy.adapters.libgdx.data.assets.GdxAssetsManager
import io.canopy.engine.app.App
import io.canopy.engine.app.desktop.DesktopCanopyAppConfig
import io.canopy.engine.app.desktop.StartupHelper
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.SceneManager
import io.canopy.platforms.desktop.graphics.managers.CameraManager
import io.canopy.platforms.desktop.graphics.systems.RenderSystem
import io.canopy.tooling.utils.UnstableApi

/** LWJGL3 implementation of [App] backed by the shared engine lifecycle. */
@UnstableApi
class DesktopCanopyApp internal constructor() : App<DesktopCanopyAppConfig>() {

    override fun provideManagers(): List<Manager> = listOf(
        CameraManager(),
        GdxAssetsManager(),
    )

    override fun SceneManager.configureSceneManager() {
        addSystem(RenderSystem(config.screenWidth, config.screenHeight))
    }

    override fun defaultConfig(): DesktopCanopyAppConfig = DesktopCanopyAppConfig()

    override fun internalLaunch(config: DesktopCanopyAppConfig, vararg args: String) {
        if (StartupHelper.startNewJvmIfRequired()) return

        val configuration = Lwjgl3ApplicationConfiguration().apply {
            setTitle(config.title)
            useVsync(true)
            setForegroundFPS(config.fps)
            setWindowedMode(config.screenWidth, config.screenHeight)
            setWindowIcon(*config.icons.toTypedArray())
            config.configure(this)
        }

        installBackendHandle(
            requestExit = {
                Gdx.app?.let { application -> application.postRunnable { application.exit() } }
            },
            forceClose = { Runtime.getRuntime().halt(0) },
        )

        val listener = object : ApplicationAdapter() {
            override fun create() = engineLoop.enter()

            override fun render() = engineLoop.update(Gdx.graphics.deltaTime)

            override fun resize(width: Int, height: Int) = engineLoop.resize(width, height)

            override fun dispose() = engineLoop.exit()
        }

        Lwjgl3Application(listener, configuration)
    }
}

@UnstableApi
fun desktopApp(builder: DesktopCanopyApp.() -> Unit = {}): DesktopCanopyApp = DesktopCanopyApp().apply(builder)
