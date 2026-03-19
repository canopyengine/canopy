package io.canopy.engine.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import io.canopy.engine.app.screen.ScreenManager
import io.canopy.engine.app.screen.ScreenRegistry
import io.canopy.engine.core.CanopyBuildInfo
import io.canopy.engine.core.managers.InjectionManager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.data.core.assets.AssetsManager
import io.canopy.engine.logging.CanopyLogging
import io.canopy.engine.logging.EngineLogs
import io.canopy.engine.logging.LogContext

abstract class App<C : AppConfig> protected constructor() {

    private val screenRegistry = ScreenRegistry()
    val sceneManager = SceneManager()
    private val screenManager = ScreenManager()

    private var _config: C? = null
    protected val config: C get() = _config ?: defaultConfig()

    private var frame: Long = 0

    protected var onReady: (App<C>) -> Unit = {}
    protected var onUpdate: (App<C>, delta: Float) -> Unit = { _, _ -> }
    protected var onResize: (App<C>, width: Int, height: Int) -> Unit = { _, _, _ -> }
    protected var onExit: (App<C>) -> Unit = {}

    private val startedLatch = CountDownLatch(1)
    private val finishedLatch = CountDownLatch(1)

    private val backendExitRef = AtomicReference<(() -> Unit)?>(null)
    private val backendForceRef = AtomicReference<(() -> Unit)?>(null)
    private val launchThreadRef = AtomicReference<Thread?>(null)
    private val launchErrorRef = AtomicReference<Throwable?>(null)

    val handle: AppHandle = AppHandle(
        onRequestExit = {
            backendExitRef.get()?.invoke() ?: launchThreadRef.get()?.interrupt()
        },
        onForceClose = {
            backendForceRef.get()?.invoke() ?: Runtime.getRuntime().halt(0)
        },
        onJoin = { timeout, unit -> finishedLatch.await(timeout, unit) },
        onAwaitStarted = { timeout, unit -> startedLatch.await(timeout, unit) }
    )

    abstract fun defaultConfig(): C

    protected abstract fun internalLaunch(config: C, vararg args: String)

    /**
     * Called by the platform runtime when the app is actually starting.
     */
    open fun ready() {
        CanopyLogging.init(
            CanopyLogging.Config(
                engineVersion = CanopyBuildInfo.projectVersion
            )
        )

        ManagersRegistry.teardown()

        val backendName = this::class.simpleName ?: "unknown"
        LogContext.with("backend" to backendName) {
            EngineLogs.lifecycle.info { "Booting Canopy..." }

            onReady(this)

            ManagersRegistry.apply {
                +InjectionManager()
                +AssetsManager()
                +sceneManager
                +screenManager
            }.setup()

            screenRegistry.setup()

            startedLatch.countDown()

            EngineLogs.lifecycle.info("event" to "app.launch.init") { "Application started." }
        }
    }

    /**
     * Called by the platform runtime every frame/tick.
     */
    open fun update(delta: Float) {
        frame++
        LogContext.with("frame" to frame) {
            onUpdate(this, delta)
        }
        screenManager.frame(delta)
    }

    /**
     * Called by the platform runtime when the surface changes size.
     */
    open fun resize(width: Int, height: Int) {
        sceneManager.resize(width, height)
        screenManager.resize(width, height)
        onResize(this, width, height)

        EngineLogs.lifecycle.debug(
            "event" to "app.resize",
            "width" to width,
            "height" to height
        ) { "Screen resized." }
    }

    /**
     * Called by the platform runtime on shutdown.
     */
    open fun exit() {
        try {
            EngineLogs.lifecycle.info("event" to "app.dispose") { "Disposing app" }
            ManagersRegistry.teardown()
            CanopyLogging.end(reason = "normal")
        } catch (t: Throwable) {
            CanopyLogging.end(reason = "crash", t = t)
            throw t
        } finally {
            onExit(this)
            finishedLatch.countDown()
        }
    }

    fun launch(vararg args: String) {
        internalLaunch(config, *args)
    }

    fun launchBlocking(vararg args: String) {
        launchAsync(threadName = "canopy-app", *args).join()
    }

    fun launchAsync(threadName: String = "canopy-app", vararg args: String): AppHandle {
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

        startedLatch.await()
        launchErrorRef.get()?.let { throw it }

        return handle
    }

    fun installBackendHandle(requestExit: () -> Unit, forceClose: (() -> Unit)? = null) {
        backendExitRef.set(requestExit)
        backendForceRef.set(forceClose ?: requestExit)
    }

    fun config(newConfig: C) {
        _config = newConfig
    }

    fun onReady(handler: App<C>.() -> Unit) {
        onReady = handler
    }

    fun onUpdate(handler: App<C>.(Float) -> Unit) {
        onUpdate = handler
    }

    fun onResize(handler: App<C>.(Int, Int) -> Unit) {
        onResize = handler
    }

    fun onExit(handler: App<C>.() -> Unit) {
        onExit = handler
    }

    fun screens(handler: ScreenRegistry.() -> Unit) {
        screenRegistry.registerSetupCallback(handler)
    }

    fun sceneManager(handler: SceneManager.() -> Unit) {
        sceneManager.handler()
    }

    fun managers(handler: ManagersRegistry.() -> Unit) {
        ManagersRegistry.apply(handler)
    }
}
