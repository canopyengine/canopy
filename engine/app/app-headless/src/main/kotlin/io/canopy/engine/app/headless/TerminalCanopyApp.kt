package io.canopy.engine.app.headless

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import io.canopy.engine.app.core.CanopyApp
import io.canopy.engine.app.core.CanopyAppConfig
import io.canopy.engine.logging.api.Logs

class TerminalCanopyApp internal constructor() : CanopyApp<CanopyAppConfig>() {
    private val log = Logs.get("canopy.app.terminal")

    override fun defaultConfig(): CanopyAppConfig = CanopyAppConfig(
        title = "Test Headless Canopy Game"
    )

    /**
     * Core owns sync/async + handle lifecycle.
     * This backend just starts the HeadlessApplication and installs exit hooks.
     */
    override fun internalLaunch(config: CanopyAppConfig, vararg args: String) {
        val headless = HeadlessApplication(this, HeadlessApplicationConfiguration())

        // As soon as libGDX is alive, install how to exit this backend.
        // (Gdx.app should be available around now, but we keep a direct reference too.)
        installBackendHandle(
            requestExit = {
                // safest: schedule on the libGDX thread when available
                val app = Gdx.app
                if (app != null) app.postRunnable { app.exit() } else headless.exit()
            },
            forceClose = {
                // Headless should exit cleanly; last resort still halts JVM.
                // You can choose to just call headless.exit() here if you prefer.
                try {
                    headless.exit()
                } finally {
                    // optional last resort:
                    // Runtime.getRuntime().halt(0)
                }
            }
        )

        // HeadlessApplication constructor returns immediately; loop runs in its own thread.
        // So internalLaunch returns quickly here (good for sync launch()).
    }
}

fun terminalApp(builder: TerminalCanopyApp.() -> Unit = {}): TerminalCanopyApp = TerminalCanopyApp().apply(builder)
