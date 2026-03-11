package io.canopy.engine.app.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import io.canopy.engine.app.core.screen.CanopyScreen
import io.canopy.engine.app.core.screen.CanopyScreenRegistry
import io.canopy.engine.core.managers.InjectionManager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.data.core.assets.AssetsManager
import io.canopy.engine.logging.CanopyLogging
import io.canopy.engine.logging.EngineLogs
import io.canopy.engine.logging.LogContext
import ktx.app.KtxGame
import ktx.async.KtxAsync

/**
 * Base application class and primary entry point for a Canopy app.
 *
 * Responsibilities:
 * - Bootstraps engine services (logging, async, managers, screens)
 * - Provides lifecycle hooks (onCreate / onRender / onResize / onDispose)
 * - Supports both blocking and async launch styles
 * - Exposes a [CanopyAppHandle] so callers can request graceful exit or force close
 *
 * Backends:
 * Subclasses implement [internalLaunch] to start a specific backend (e.g., LWJGL3).
 * Some backends block until exit; others may return immediately.
 */
abstract class CanopyApp<C : CanopyAppConfig> protected constructor(isGraphical: Boolean = true) :
    KtxGame<CanopyScreen>(clearScreen = isGraphical) {

    /* ============================================================
     * App state
     * ============================================================ */

    private val screenRegistry = CanopyScreenRegistry(this)
    val sceneManager: SceneManager = SceneManager()

    private var _config: C? = null
    protected val config: C get() = _config ?: defaultConfig()

    private var frame: Long = 0

    /* ============================================================
     * Lifecycle callbacks (user hooks)
     * ============================================================ */

    protected var onCreate: (CanopyApp<C>) -> Unit = {}
    protected var onRender: (CanopyApp<C>) -> Unit = {}
    protected var onResize: (CanopyApp<C>, width: Int, height: Int) -> Unit = { _, _, _ -> }
    protected var onDispose: (CanopyApp<C>) -> Unit = {}

    /* ============================================================
     * Async launch / app handle plumbing
     * ============================================================ */

    /**
     * Signals:
     * - startedLatch: boot completed (logging/managers/screens installed)
     * - finishedLatch: application finished (dispose completed / thread ended)
     */
    private val startedLatch = CountDownLatch(1)
    private val finishedLatch = CountDownLatch(1)

    /**
     * Exit hooks provided by the backend once it is initialized.
     * Until installed, the handle falls back to best-effort mechanisms.
     */
    private val backendExitRef = AtomicReference<(() -> Unit)?>(null)
    private val backendForceRef = AtomicReference<(() -> Unit)?>(null)

    private val launchThreadRef = AtomicReference<Thread?>(null)
    private val launchErrorRef = AtomicReference<Throwable?>(null)

    val handle: CanopyAppHandle = ProxyAppHandle(
        finished = finishedLatch,
        onRequestExit = {
            backendExitRef.get()?.invoke() ?: run {
                // Backend not installed yet: best-effort exit by interrupting the launch thread.
                launchThreadRef.get()?.interrupt()
            }
        },
        onForceClose = {
            backendForceRef.get()?.invoke() ?: run {
                // Last resort: halt the JVM. Prefer graceful shutdown when possible.
                Runtime.getRuntime().halt(0)
            }
        },
        onAwaitStarted = { timeout, timeUnit -> startedLatch.await(timeout, timeUnit) }
    )

    /* ============================================================
     * Engine lifecycle (KtxGame hooks)
     * ============================================================ */

    override fun create() {
        CanopyLogging.init()

        // Provide backend identity via MDC for all logs produced during boot.
        val backendName = this::class.simpleName ?: "unknown"
        LogContext.with("backend" to backendName) {
            // No need to also attach "backend" as per-call fields: it's already in MDC.
            EngineLogs.lifecycle.info { "Booting Canopy..." }

            onCreate(this)

            KtxAsync.initiate()

            ManagersRegistry.apply {
                +InjectionManager()
                +AssetsManager()
                +sceneManager
            }.setup()

            screenRegistry.setup()

            // Unblock async launch callers (handle.awaitStarted / launchAsync waiting).
            startedLatch.countDown()

            super.create()

            EngineLogs.lifecycle.info("event" to "app.launch.init") { "Application started." }
        }
    }

    override fun render() {
        frame++
        LogContext.with("frame" to frame) {
            EngineLogs.lifecycle.debug("frame" to frame) { "Rendering frame..." }
            onRender(this)
            EngineLogs.lifecycle.debug("rendering" to frame) { "Frame rendered." }
            super.render()
        }
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        sceneManager.resize(width, height)
        onResize(this, width, height)
        EngineLogs.lifecycle.debug(
            "event" to "app.resize",
            "width" to width,
            "height" to height
        ) { "Screen resized." }
    }

    override fun dispose() {
        try {
            EngineLogs.lifecycle.info("event" to "app.dispose") { "Disposing app" }
            ManagersRegistry.teardown()
            CanopyLogging.end(reason = "normal")
        } catch (t: Throwable) {
            CanopyLogging.end(reason = "crash", t = t)
            throw t
        } finally {
            onDispose(this)
            finishedLatch.countDown()
            super.dispose()
        }
    }

    /* ============================================================
     * Configuration + backend contract
     * ============================================================ */

    abstract fun defaultConfig(): C

    /**
     * Backend-specific launch implementation.
     *
     * Backend implementer checklist:
     * - Call [installBackendHandle] once the backend can handle exit requests.
     * - Ensure that backend shutdown triggers [dispose] (so teardown + session end logs happen).
     * - If the backend blocks (common), this method may not return until exit.
     * - If the backend is non-blocking, the method may return immediately.
     */
    protected abstract fun internalLaunch(config: C, vararg args: String)

    fun launchBlocking(vararg args: String) {
        launchAsync(threadName = "canopy-app", *args).join()
    }

    fun launch(vararg args: String) {
        internalLaunch(config, *args)
    }

    fun launchAsync(threadName: String = "canopy-app", vararg args: String): CanopyAppHandle {
        val runnable = {
            try {
                internalLaunch(config, *args)
            } catch (t: Throwable) {
                launchErrorRef.set(t)
                throw t
            } finally {
                finishedLatch.countDown()
            }
        }

        val t = Thread(runnable, threadName).apply { isDaemon = false }
        launchThreadRef.set(t)
        t.start()

        // Wait until boot is complete so the handle can safely request exit.
        startedLatch.await()

        launchErrorRef.get()?.let { throw it }

        return handle
    }

    /**
     * Installs backend exit callbacks so external callers can stop the app cleanly.
     *
     * @param requestExit graceful shutdown request (close window / stop loop)
     * @param forceClose optional hard shutdown; defaults to [requestExit] if omitted
     */
    protected fun installBackendHandle(requestExit: () -> Unit, forceClose: (() -> Unit)? = null) {
        backendExitRef.set(requestExit)
        backendForceRef.set(forceClose ?: requestExit)
    }

    /* ============================================================
     * Declarative builder helpers (DSL-like)
     * ============================================================ */

    fun sceneManager(lambda: SceneManager.() -> Unit) {
        sceneManager.apply(lambda)
    }

    fun config(newConfig: C) {
        _config = newConfig
    }

    fun onCreate(handler: CanopyApp<C>.() -> Unit) {
        onCreate = handler
    }

    fun onRender(handler: CanopyApp<C>.() -> Unit) {
        onRender = handler
    }

    fun onResize(handler: CanopyApp<C>.(Int, Int) -> Unit) {
        onResize = handler
    }

    fun onDispose(handler: CanopyApp<C>.() -> Unit) {
        onDispose = handler
    }

    fun screens(handler: CanopyScreenRegistry.() -> Unit) {
        screenRegistry.registerSetupCallback(handler)
    }

    fun managers(handler: ManagersRegistry.() -> Unit) {
        ManagersRegistry.apply(handler)
    }
}

private class ProxyAppHandle(
    private val finished: CountDownLatch,
    onRequestExit: () -> Unit,
    onForceClose: () -> Unit,
    onAwaitStarted: (Long, TimeUnit) -> Boolean,
) : CanopyAppHandle(
    onRequestExit,
    onForceClose,
    onJoin = { timeout, unit -> finished.await(timeout, unit) },
    onAwaitStarted
)
