package io.canopy.devtools.app

import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.platforms.headless.app.HeadlessApp
import io.canopy.platforms.headless.app.headlessApp

class AppTestDriver<C : AppConfig> internal constructor(private val app: App<C>) {
    fun start() = app.ready()
    fun frame(delta: Float) = app.update(delta)
    fun resize(w: Int, h: Int) = app.resize(w, h)
    fun stop() = app.exit()

    fun launch() = app.launch()

    fun launchAsync() = app.launchAsync()
}

fun <C : AppConfig> appTestDriver(app: App<C>) = AppTestDriver(app)

fun testHeadlessApp(builder: HeadlessApp.() -> Unit = {}) = appTestDriver(headlessApp(builder))
