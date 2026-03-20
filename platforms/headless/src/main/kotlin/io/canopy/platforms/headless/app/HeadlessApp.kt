package io.canopy.platforms.headless.app

import io.canopy.adapters.libgdx.app.headless.HeadlessHost
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig

class HeadlessApp internal constructor() : App<AppConfig>() {

    override fun defaultConfig(): AppConfig = AppConfig(
        title = "Headless Canopy App"
    )

    override fun internalLaunch(config: AppConfig, vararg args: String) {
        HeadlessHost.launch(this)
    }
}

fun headlessApp(builder: HeadlessApp.() -> Unit = {}): HeadlessApp = HeadlessApp().apply(builder)
