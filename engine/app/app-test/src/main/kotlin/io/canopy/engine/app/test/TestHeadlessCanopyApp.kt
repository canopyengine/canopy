package io.canopy.engine.app.test

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import io.canopy.engine.app.core.CanopyApp
import io.canopy.engine.app.core.CanopyAppConfig

/**
 * Headless Canopy application backend intended for tests.
 *
 * Goals:
 * - Deterministic behavior (avoid backend-driven side effects)
 * - Isolation between tests (no leaking managers / global state)
 * - A reliable shutdown mechanism for CI
 *
 * Differences from other backends:
 * - Lifecycle methods like render/pause/resume are overridden to no-op to keep
 *   tests focused on explicit actions rather than frame-driven behavior.
 * - Global manager state is cleared on construction to avoid cross-test contamination.
 */
class TestHeadlessCanopyApp internal constructor() : CanopyApp<CanopyAppConfig>() {

    // Test backend: no-op lifecycle for deterministic tests.
    // If a specific test needs ticking, it should drive it explicitly.
    override fun render() {}
    override fun pause() {}
    override fun resume() {}

    override fun defaultConfig(): CanopyAppConfig = CanopyAppConfig(
        title = "Test Headless Canopy Game"
    )

    /**
     * Starts the LibGDX headless runtime and installs exit hooks for [CanopyAppHandle].
     *
     * Core (CanopyApp) owns:
     * - sync/async launch semantics
     * - latches + handle lifecycle
     * - boot order (logging/managers/screens)
     *
     * This backend only:
     * - constructs the headless application
     * - wires graceful/forced exit behavior
     */
    override fun internalLaunch(config: CanopyAppConfig, vararg args: String) {
        val headless = HeadlessApplication(this, HeadlessApplicationConfiguration())

        installBackendHandle(
            requestExit = {
                // Prefer posting exit on the LibGDX thread when available.
                val app = Gdx.app
                if (app != null) app.postRunnable { app.exit() } else headless.exit()
            },
            forceClose = {
                // For tests, prefer a clean exit. Avoid Runtime.halt in the testkit by default.
                headless.exit()
            }
        )

        // HeadlessApplication returns immediately; the loop runs on its own thread.
        // Tests should use the CanopyAppHandle to await start/exit where needed.
    }
}

/**
 * Convenience DSL entry point for building a test headless app.
 *
 * Example:
 * ```
 * val app = testHeadlessApp {
 *   onCreate { ... }
 *   screens { ... }
 * }
 *
 * val handle = app.launchAsync()
 * handle.awaitStarted(5, TimeUnit.SECONDS)
 * handle.requestExit()
 * handle.join()
 * ```
 */
fun testHeadlessApp(builder: TestHeadlessCanopyApp.() -> Unit = {}): TestHeadlessCanopyApp =
    TestHeadlessCanopyApp().apply(builder)
