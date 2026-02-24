package io.canopy.engine.app.test

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import io.canopy.engine.app.core.CanopyApp
import io.canopy.engine.app.core.CanopyAppConfig

class TestHeadlessCanopyApp internal constructor() : CanopyApp<CanopyAppConfig>() {
    // Testkit: no-op lifecycle for deterministic tests
    override fun render() {}
    override fun pause() {}
    override fun resume() {}

    override fun defaultConfig(): CanopyAppConfig = CanopyAppConfig(
        title = "Test Headless Canopy Game"
    )

    /**
     * Core owns sync/async + handle lifecycle.
     * This backend only starts the HeadlessApplication and installs exit hooks.
     */
    override fun internalLaunch(config: CanopyAppConfig, vararg args: String) {
        val headless = HeadlessApplication(this, HeadlessApplicationConfiguration())

        installBackendHandle(
            requestExit = {
                val app = Gdx.app
                if (app != null) app.postRunnable { app.exit() } else headless.exit()
            },
            forceClose = {
                // For tests, prefer a clean exit. Keep JVM halt out of testkit by default.
                headless.exit()
            }
        )
    }
}

fun testHeadlessApp(builder: TestHeadlessCanopyApp.() -> Unit): TestHeadlessCanopyApp =
    TestHeadlessCanopyApp().apply(builder)
