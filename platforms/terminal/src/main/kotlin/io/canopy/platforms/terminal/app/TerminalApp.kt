package io.canopy.platforms.terminal.app

import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.adapters.libgdx.app.headless.HeadlessHost
import io.canopy.adapters.libgdx.data.assets.GdxAssetsManager
import io.canopy.adapters.libgdx.input.GdxInputManager
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.logging.EngineLogs

class TerminalApp internal constructor() : App<AppConfig>() {

    private val log = EngineLogs.app
    private val terminal = Terminal()

    private val inputManager = GdxInputManager()
    private val assetsManager = GdxAssetsManager()

    override fun defaultConfig(): AppConfig = AppConfig(
        title = "Terminal Canopy App"
    )

    override fun collectManagers(): List<Manager> = listOf(
        inputManager,
        assetsManager
    )

    override fun internalLaunch(config: AppConfig, vararg args: String) {
        log.info { "Starting terminal runtime" }
        HeadlessHost.launch(this)
    }
}

fun terminalApp(builder: TerminalApp.() -> Unit = {}): TerminalApp = TerminalApp().apply(builder)
