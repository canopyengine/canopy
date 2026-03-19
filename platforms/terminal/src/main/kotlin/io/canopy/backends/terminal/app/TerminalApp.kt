package io.canopy.backends.terminal.app

import com.github.ajalt.mordant.terminal.Terminal
import io.canopy.backends.headless.app.HeadlessHost
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.logging.EngineLogs

class TerminalApp internal constructor() : App<AppConfig>() {

    private val log = EngineLogs.app
    private val terminal = Terminal()

    override fun defaultConfig(): AppConfig = AppConfig(
        title = "Terminal Canopy App"
    )

    override fun internalLaunch(config: AppConfig, vararg args: String) {
        log.info { "Starting terminal runtime" }

        HeadlessHost.launch(this)
    }
}

fun terminalApp(builder: TerminalApp.() -> Unit = {}): TerminalApp = TerminalApp().apply(builder)
