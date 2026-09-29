package io.canopy.platforms.terminal.app

import io.canopy.adapters.mordant.input.MordantInputManager
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.logging.EngineLogs
import io.canopy.platforms.terminal.data.assets.TerminalAssetsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Test variant of TerminalApp that doesn't start the real terminal loop.
 * Use for unit tests that need the app structure but not the actual terminal.
 */
class TestTerminalApp internal constructor() : App<AppConfig>() {

    private val log = EngineLogs.app

    private val inputManager = MordantInputManager()
    private val assetsManager = TerminalAssetsManager()

    // App-wide coroutine scope
    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun defaultConfig(): AppConfig = AppConfig(
        title = "Test Terminal Canopy App"
    )

    override fun provideManagers() = listOf(
        inputManager,
        assetsManager
    )

    override fun internalLaunch(config: AppConfig, vararg args: String) {
        log.info { "Starting TEST terminal runtime (no real terminal)" }

        installBackendHandle(
            requestExit = { },
            forceClose = { }
        )

        enter()

        // In test mode, we don't run the actual terminal loop
        // Tests drive frames manually via AppTestDriver
    }
}

fun testTerminalApp(builder: TestTerminalApp.() -> Unit = {}): TestTerminalApp = TestTerminalApp().apply(builder)
