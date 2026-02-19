package canopy.graphics.backends

import canopy.app.game.CanopyGame
import canopy.app.game.CanopyGameHandle
import canopy.core.logging.logger
import canopy.core.managers.ManagersRegistry
import canopy.core.managers.SceneManager
import canopy.data.assets.AssetsManager
import canopy.graphics.managers.CameraManager
import canopy.graphics.systems.RenderSystem
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import ktx.async.KtxAsync

class CanopyDesktopGame(
    // General props
    sceneManager: SceneManager = SceneManager(),
    config: CanopyDesktopGameConfig? = null,
    onCreate: (CanopyGame<CanopyDesktopGameConfig>) -> Unit = {},
    onResize: (CanopyGame<CanopyDesktopGameConfig>, width: Int, height: Int) -> Unit = { _, _, _ -> },
    onDispose: (CanopyGame<CanopyDesktopGameConfig>) -> Unit = {},
) : CanopyGame<CanopyDesktopGameConfig>(
    sceneManager,
    config,
    onCreate,
    onResize,
    onDispose
) {
    private val logger = logger<CanopyDesktopGame>()

    override fun create() {
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

    override fun defaultConfig(): CanopyDesktopGameConfig = CanopyDesktopGameConfig()

    override fun internalLaunch(config: CanopyDesktopGameConfig, vararg args: String): CanopyGameHandle {
        val handle = CanopyGameHandle {}

        if (StartupHelper.startNewJvmIfRequired()) {
            logger.warn { "A new JVM was spammed while booting Canopy. Quitting..." }
            return handle
        }

        val cfg =
            Lwjgl3ApplicationConfiguration().apply {
                setTitle(config.title)
                // // Vsync limits the frames per second to what your hardware can display, and helps eliminate
                // // screen tearing. This setting doesn't always work on Linux, so the line after is a safeguard.
                useVsync(true)
                // // Limits FPS to the refresh rate of the currently active monitor, plus 1 to try to match fractional
                // // refresh rates. The Vsync setting above should limit the actual FPS to match the monitor.
                setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1)
                // // If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
                // // useful for testing performance, but can also be very stressful to some hardware.
                // // You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.

                setWindowedMode(config.screenWidth, config.screenHeight)
                // // You can change these files; they are in lwjgl3/src/main/resources/ .
                // // They can also be loaded from the root of assets/ .
                setWindowIcon(*config.icons.toTypedArray())

                // // This could improve compatibility with Windows machines with buggy OpenGL drivers, Macs
                // // with Apple Silicon that have to emulate compatibility with OpenGL anyway, and more.
                // // This uses the dependency `com.badlogicgames.gdx:gdx-lwjgl3-angle` to function.
                // // You would need to add this line to lwjgl3/build.gradle , below the dependency on `gdx-backend-lwjgl3`:
                // //     implementation "com.badlogicgames.gdx:gdx-lwjgl3-angle:$gdxVersion"
                // // You can choose to add the following line and the mentioned dependency if you want; they
                // // are not intended for games that use GL30 (which is compatibility with OpenGL ES 3.0).
                // // Know that it might not work well in some cases.
                // setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.ANGLE_GLES20, 0, 0)
                config.configure(this)
            }

        val app = Lwjgl3Application(this, cfg)

        return CanopyGameHandle { app.exit() }
    }
}
