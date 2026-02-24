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

abstract class CanopyApp<C : CanopyAppConfig> protected constructor() : KtxGame<CanopyScreen>() {
    private val screenRegistry = CanopyScreenRegistry(this)

    val sceneManager: SceneManager = SceneManager()
    protected var onCreate: (CanopyApp<C>) -> Unit = {}
    protected var onResize: (CanopyApp<C>, width: Int, height: Int) -> Unit = { _, _, _ -> }
    protected var onDispose: (CanopyApp<C>) -> Unit = {}
    protected var logLevel: LogLevel = LogLevel.DEBUG

    private val started = CountDownLatch(1)
    private val finished = CountDownLatch(1)

    private val backendExitRef = AtomicReference<(() -> Unit)?>(null)
    private val backendForceRef = AtomicReference<(() -> Unit)?>(null)

    // always-available handle
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

    private val launchThreadRef = AtomicReference<Thread?>(null)
    private val launchErrorRef = AtomicReference<Throwable?>(null)

    protected val injectionManager by lazy { ManagersRegistry.get(InjectionManager::class) }

    private var _config: C? = null
    protected val config: C get() = _config ?: defaultConfig()

    private var frame: Long = 0

    override fun create() {
        // Init logging FIRST (so startup logs are captured)
        val runId = CanopyLogging.defaultRunId()
        val logDir = CanopyLogging.defaultBaseLogDir()
        val engineVersion = CanopyBuildInfo.version

        ConsoleBanner.print(CanopyBuildInfo.version, ConsoleBanner.Mode.GRADIENT)

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

        EngineLogs.lifecycle.info(
            fields = mapOf(
                "event" to "app.launch",
                "backend" to (this::class.simpleName ?: "unknown")
            )
        ) { "Launching app" }

        KtxAsync.initiate()

        ManagersRegistry.apply {
            register(InjectionManager())
            register(sceneManager)
        }.setup()

        onCreate(this)

        screenRegistry.setup()

        started.countDown()
        super.create()
    }

    override fun render() {
        frame++
        LogContext.with("frame" to frame) {
            super.render()
        }
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        sceneManager.resize(width, height)
        onResize(this, width, height)
    }

    override fun dispose() {
        try {
            EngineLogs.lifecycle.info(fields = mapOf("event" to "app.dispose")) { "Disposing app" }
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

    fun onResize(handler: CanopyApp<C>.(Int, Int) -> Unit) {
        onResize = handler
    }

    fun onDispose(handler: CanopyApp<C>.() -> Unit) {
        onDispose = handler
    }

    fun screens(handler: CanopyScreenRegistry.() -> Unit) {
        screenRegistry.setupCallback = handler
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
