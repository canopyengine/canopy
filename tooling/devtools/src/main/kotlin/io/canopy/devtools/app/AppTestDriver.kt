package io.canopy.devtools.app

import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.platforms.headless.app.HeadlessApp
import io.canopy.platforms.headless.app.headlessApp

/** Drives the shared lifecycle directly for tests, or delegates to the platform launch methods. */
class AppTestDriver<C : AppConfig> internal constructor(private val app: App<C>) {
    /** Enters the engine lifecycle without launching the platform backend. */
    fun start() = app.engineLoop.enter()

    /** Advances one frame with delta in seconds, including fixed physics steps. */
    fun frame(delta: Float) = app.engineLoop.update(delta)

    /** Delivers new dimensions to the engine lifecycle. */
    fun resize(w: Int, h: Int) = app.engineLoop.resize(w, h)

    /** Exits the engine lifecycle and tears down managers. */
    fun stop() = app.engineLoop.exit()

    /** Delegates launch to the application platform. */
    fun launch() = app.launch()

    /** Launches the platform on a dedicated thread and returns its handle. */
    fun launchAsync() = app.launchAsync()
}

/** Wraps an existing application without starting it. */
fun <C : AppConfig> appTestDriver(app: App<C>) = AppTestDriver(app)

/** Builds a headless application and wraps it for direct lifecycle testing. */
fun testHeadlessApp(builder: HeadlessApp.() -> Unit = {}) = appTestDriver(headlessApp(builder))
