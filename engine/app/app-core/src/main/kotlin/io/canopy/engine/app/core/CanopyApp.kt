package io.canopy.engine.app.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import io.canopy.engine.app.core.screen.CanopyScreen
import io.canopy.engine.app.core.screen.CanopyScreenRegistry
import io.canopy.engine.core.CanopyBuildInfo
import io.canopy.engine.core.managers.InjectionManager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.logging.api.LogContext
import io.canopy.engine.logging.api.LogLevel
import io.canopy.engine.logging.bootstrap.CanopyLogging
import io.canopy.engine.logging.engine.EngineLogs
import ktx.app.KtxGame
import ktx.async.KtxAsync

/**
 * Base App class - starting point of a Canopy App
 */
abstract class CanopyApp<C : CanopyAppConfig> protected constructor() : KtxGame<CanopyScreen>() {
    /*  ====================
     *     App properties
     *  ==================== */
    private val screenRegistry = CanopyScreenRegistry(this)
    val sceneManager: SceneManager = SceneManager()

    /* App config */
    private var _config: C? = null
    protected val config: C get() = _config ?: defaultConfig()

    /* Holds track of frame count */
    private var frame: Long = 0

    /*  ========================
     *      Lifecycle callbacks
     *  ======================== */
    protected var onCreate: (CanopyApp<C>) -> Unit = {}
    protected var onRender: (CanopyApp<C>) -> Unit = {}
    protected var onResize: (CanopyApp<C>, width: Int, height: Int) -> Unit = { _, _, _ -> }
    protected var onDispose: (CanopyApp<C>) -> Unit = {}
    protected var logLevel: LogLevel = LogLevel.DEBUG

    /*  =============================================
     *      Async helpers - allow async app handling
     *  ============================================= */

    // Countdown latches - to control when an app started and finished
    private val started = CountDownLatch(1)
    private val finished = CountDownLatch(1)

    // Refs to callbacks - used to 'install' the handler before running the app
    private val backendExitRef = AtomicReference<(() -> Unit)?>(null)
    private val backendForceRef = AtomicReference<(() -> Unit)?>(null)

    // References the launch thread
    private val launchThreadRef = AtomicReference<Thread?>(null)

    // Reference errors thrown by the launch thread
    private val launchErrorRef = AtomicReference<Throwable?>(null)

    /*
     * App Handle - useful for forcing or scheduling app close
     */
    val handle: CanopyAppHandle = ProxyAppHandle(
        finished = finished,
        onRequestExit = {
            backendExitRef.get()?.invoke() ?: run {
                // if backend not installed yet, best-effort: interrupt launch thread (set by launchAsync)
                launchThreadRef.get()?.interrupt()
            }
        },
        onForceClose = {
            backendForceRef.get()?.invoke() ?: run {
                // last resort; you can prefer waiting then halting
                Runtime.getRuntime().halt(0)
            }
        },
        onAwaitStarted = { timeout, timeUnit -> started.await(timeout, timeUnit) }
    )

    /* =========================================
     *      LIFECYCLE OPERATIONS
     * =========================================
     */

    /**
     * Called on app setup - similar to nodes' 'onReady' callbacks
     */
    override fun create() {
        // Init logging FIRST (so startup logs are captured)
        val runId = CanopyLogging.defaultRunId()
        val logDir = CanopyLogging.defaultBaseLogDir()
        val engineVersion = CanopyBuildInfo.projectVersion

        CanopyLogging.init(
            CanopyLogging.Config(
                baseLogDir = logDir,
                runId = runId,
                engineVersion = engineVersion,
                consoleLevel = LogLevel.INFO,
                engineLogLevel = LogLevel.INFO,
                userLogLevel = logLevel
            )
        )

        LogContext.with(
            "backend" to (this::class.simpleName ?: "unknown")
        ) {
            EngineLogs.lifecycle.info(
                "backend" to (this::class.simpleName ?: "unknown")
            ) { "Booting Canopy..." }

            // Should be called before managers are registered just in case the user decides to do setup here
            onCreate(this)

            // Allows async asset loading
            KtxAsync.initiate()

            // Register global managers
            ManagersRegistry.apply {
                +InjectionManager()
                +sceneManager
            }.setup()

            // Screens added on the screen registry are added here
            screenRegistry.setup()

            // Countdown so main thread stops blocking after app boots
            started.countDown()
            super.create()

            EngineLogs.lifecycle.info(
                "event" to "app.launch.init"
            ) { "Application started." }
        }
    }

    /**
     * Called on each game loop - equivalent to 'update'
     */
    override fun render() {
        frame++
        LogContext.with("frame" to frame) {
            onRender(this)
            super.render()
        }
    }

    /**
     * Called on screen resize
     */
    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        sceneManager.resize(width, height)
        onResize(this, width, height)
    }

    /**
     * Called on app disposal
     */
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
            finished.countDown()
            super.dispose()
        }
    }

    /**
     * Default config
     */
    abstract fun defaultConfig(): C

    /**
     * Backend-specific start. May block until the app exits (LWJGL3 does).
     * Returns a backend handle (maybe "post-exit" for blocking backends).
     */
    protected abstract fun internalLaunch(config: C, vararg args: String)

    /**
     * Fully synchronous "run": starts and blocks until the app exits.
     */
    fun launchBlocking(vararg args: String) {
        // For non-blocking backends, this ensures the caller still blocks until done.
        // For blocking backends, join() will return immediately since launch() returns post-exit.
        launchAsync(threadName = "canopy-app", *args).join()
    }

    fun launch(vararg args: String) {
        internalLaunch(config, *args)
    }

    /** Async launch returns immediately with [handle]. */
    fun launchAsync(threadName: String = "canopy-app", vararg args: String): CanopyAppHandle {
        val t = Thread({
            try {
                internalLaunch(config, *args)
            } catch (t: Throwable) {
                launchErrorRef.set(t)
                throw t
            } finally {
                finished.countDown()
            }
        }, threadName).apply {
            isDaemon = false
        }

        launchThreadRef.set(t)
        t.start()

        // Ensure thread is started so handle.requestExit() can at least interrupt it
        started.await()

        launchErrorRef.get()?.let { throw it }

        return handle
    }

    protected fun installBackendHandle(requestExit: () -> Unit, forceClose: (() -> Unit)? = null) {
        backendExitRef.set(requestExit)
        backendForceRef.set(forceClose ?: requestExit)
    }

    /** ===================================
     *      Declarative builder helpers
     *  =================================== */

    /* Helper methods for building the app */
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
