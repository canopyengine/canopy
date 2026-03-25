package io.canopy.platforms.terminal.app

import com.github.ajalt.mordant.input.receiveEvents
import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.adapters.libgdx.data.assets.GdxAssetsManager
import io.canopy.adapters.mordant.input.MordantInputManager
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.data.saving.SaveManager
import io.canopy.engine.logging.EngineLogs

class TerminalApp internal constructor() : App<AppConfig>() {

    private val log = EngineLogs.app
    private val terminal = Terminal()

    private val inputManager = MordantInputManager(terminal)
    private val assetsManager = GdxAssetsManager()

    override fun defaultConfig(): AppConfig = AppConfig(
        title = "Terminal Canopy App"
    )

    override fun collectManagers() = listOf(
        SaveManager(),
        inputManager,
        assetsManager
    )

    override fun afterReady() {
        inputManager.receiveEvents()
    }

    override fun internalLaunch(config: AppConfig, vararg args: String) {
        log.info { "Starting terminal runtime" }
        val frameNanos = 1_000_000_000L / config.fps
        var running = true

        installBackendHandle(
            requestExit = { running = false },
            forceClose = { running = false }
        )

        ready()

        var lastTime = System.nanoTime()

        while (running && !Thread.currentThread().isInterrupted) {
            val now = System.nanoTime()
            val deltaNanos = now - lastTime
            lastTime = now

            val delta = deltaNanos / 1_000_000_000f

            update(delta)

            val elapsed = System.nanoTime() - now
            val sleepNanos = frameNanos - elapsed
            if (sleepNanos > 0) {
                Thread.sleep(sleepNanos / 1_000_000L, (sleepNanos % 1_000_000L).toInt())
            }
        }

        exit()
    }
}

fun terminalApp(builder: TerminalApp.() -> Unit = {}): TerminalApp = TerminalApp().apply(builder)
