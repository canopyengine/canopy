package io.canopy.engine.app.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import io.canopy.engine.app.core.CanopyApp
import io.canopy.engine.app.core.CanopyAppHandle
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.data.core.assets.AssetsManager
import io.canopy.engine.graphics.managers.CameraManager
import io.canopy.engine.graphics.systems.RenderSystem
import io.canopy.engine.logging.api.Logs
import io.canopy.engine.logging.engine.EngineLogs
import io.canopy.engine.utils.UnstableApi
import ktx.async.KtxAsync

@UnstableApi
class DesktopCanopyApp(
    sceneManager: SceneManager = SceneManager(),
    config: DesktopCanopyAppConfig? = null,
    onCreate: (CanopyApp<DesktopCanopyAppConfig>) -> Unit = {},
    onResize: (CanopyApp<DesktopCanopyAppConfig>, width: Int, height: Int) -> Unit = { _, _, _ -> },
    onDispose: (CanopyApp<DesktopCanopyAppConfig>) -> Unit = {},
) : CanopyApp<DesktopCanopyAppConfig>(
    sceneManager,
    config,
    onCreate,
    onResize,
    onDispose
) {
    // App/variant logger (not engine.*)
    private val log = Logs.get("canopy.app.desktop")

    override fun create() {
        // Engine lifecycle logs should go through EngineLogs
        EngineLogs.lifecycle.debug(
            fields = mapOf(
                "event" to "desktop.create",
                "screenWidth" to config.screenWidth,
                "screenHeight" to config.screenHeight
            )
        ) { "DesktopCanopyApp.create()" }

        KtxAsync.initiate()

        sceneManager.apply {
            registerSystem(RenderSystem(config.screenWidth, config.screenHeight))
        }

        ManagersRegistry.apply {
            register(CameraManager())
            register(AssetsManager())
        }

        super.create()
    }

    override fun defaultConfig(): DesktopCanopyAppConfig = DesktopCanopyAppConfig()

    override fun internalLaunch(config: DesktopCanopyAppConfig, vararg args: String): CanopyAppHandle {
        val handle = CanopyAppHandle {}

        log.info(
            fields = mapOf(
                "event" to "desktop.launch",
                "title" to config.title,
                "screenWidth" to config.screenWidth,
                "screenHeight" to config.screenHeight,
                "icons" to config.icons.size,
                "argsCount" to args.size
            )
        ) { "Launching desktop app" }

        if (StartupHelper.startNewJvmIfRequired()) {
            log.warn(fields = mapOf("event" to "desktop.launch.spawned_new_jvm")) {
                "A new JVM was spawned while booting Canopy. Quitting..."
            }
            return handle
        }

        val cfg = Lwjgl3ApplicationConfiguration().apply {
            setTitle(config.title)

            useVsync(true)
            setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1)

            setWindowedMode(config.screenWidth, config.screenHeight)
            setWindowIcon(*config.icons.toTypedArray())

            config.configure(this)
        }

        EngineLogs.lifecycle.info(
            fields = mapOf(
                "event" to "desktop.lwjgl3.start",
                "title" to config.title,
                "screenWidth" to config.screenWidth,
                "screenHeight" to config.screenHeight
            )
        ) { "Starting LWJGL3 application" }

        val app = Lwjgl3Application(this, cfg)

        return CanopyAppHandle {
            log.info(fields = mapOf("event" to "desktop.exit")) { "Exiting desktop app" }
            app.exit()
        }
    }
}
