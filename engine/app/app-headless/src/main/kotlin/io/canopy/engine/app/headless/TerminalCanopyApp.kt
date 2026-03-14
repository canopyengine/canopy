package io.canopy.engine.app.headless

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import io.canopy.engine.app.core.CanopyApp
import io.canopy.engine.app.core.CanopyAppConfig
import io.canopy.engine.logging.EngineLogs

/**
 * Headless (no window) Canopy application backend.
 *
 * Intended use cases:
 * - Automated tests / CI
 * - Server-side simulation
 * - Tools that need the engine loop without graphics
 *
 * Notes about lifecycle:
 * - The engine core owns boot/teardown ordering (logging, managers, screens).
 * - This backend is responsible only for starting LibGDX's headless runtime
 *   and wiring shutdown hooks through [installBackendHandle].
 */
class TerminalCanopyApp internal constructor() : CanopyApp<CanopyAppConfig>(isGraphical = false) {

    private val log = EngineLogs.app

    override fun defaultConfig(): CanopyAppConfig = CanopyAppConfig(
        title = "Test Headless Canopy Game"
    )

    override fun create() {
        super.create()
        log.info { "Starting headless backend" }
    }

    /**
     * Headless backend does not render graphics. The engine loop is still driven
     * by LibGDX, but we intentionally skip the normal render path.
     *
     * (If you later want simulation updates here, you can call super.render()
     * or invoke your own tick logic.)
     */
    override fun render() {}

    /**
     * Starts the LibGDX headless application and installs exit/force-close hooks.
     *
     * Important:
     * - [HeadlessApplication] starts its loop on its own thread and returns immediately.
     * - This method may therefore return quickly even though the app keeps running.
     * - The core layer (CanopyApp) handles sync/async launch semantics via latches/handle.
     */
    override fun internalLaunch(config: CanopyAppConfig, vararg args: String) {
        log.info { "Starting headless backend" }

        val headless = HeadlessApplication(this, HeadlessApplicationConfiguration())

        // As soon as libGDX is alive, install how to exit this backend.
        // We prefer using Gdx.app when available so we can schedule exit on the libGDX thread.
        installBackendHandle(
            requestExit = {
                val app = Gdx.app
                if (app != null) {
                    // Schedule exit on the libGDX thread to avoid threading edge-cases.
                    app.postRunnable { app.exit() }
                } else {
                    // Fallback: use the direct HeadlessApplication reference.
                    headless.exit()
                }
            },
            forceClose = {
                // Headless should exit cleanly; this is here for callers that require a "hard" stop.
                // If you ever see hangs in CI, you can consider adding Runtime.halt(0) as a last resort.
                headless.exit()
            }
        )

        // HeadlessApplication constructor returns immediately; the loop runs in its own thread.
        // internalLaunch returning quickly is expected.
    }
}

/**
 * Convenience DSL entry point for building a headless app.
 *
 * Example:
 * ```
 * terminalApp {
 *   onCreate { ... }
 *   screens { ... }
 * }.launchBlocking()
 * ```
 */
fun terminalApp(builder: TerminalCanopyApp.() -> Unit = {}): TerminalCanopyApp = TerminalCanopyApp().apply(builder)
