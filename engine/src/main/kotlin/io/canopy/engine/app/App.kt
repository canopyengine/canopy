package io.canopy.engine.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import io.canopy.engine.core.CanopyBuildInfo
import io.canopy.engine.core.managers.InjectionManager
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.logging.CanopyLogging
import io.canopy.engine.logging.EngineLogs
import io.canopy.engine.logging.LogContext

abstract class App<C : AppConfig> protected constructor() {

    /* ============================================================
     * Core systems
     * ============================================================ */

    private val screenRegistry = ScreenRegistry()

    protected val screenManager = ScreenManager()
    protected val sceneManager = SceneManager()

    /* ============================================================
     * Configuration
     * ============================================================ */

    private var _config: C? = null
    protected val config: C
        get() = _config ?: defaultConfig()

    abstract fun defaultConfig(): C

    /* ============================================================
     * Runtime state
     * ============================================================ */

    private var frame: Long = 0

    private val startedLatch = CountDownLatch(1)
    private val finishedLatch = CountDownLatch(1)
    private val finished = AtomicBoolean(false)

    private val backendExitRef = AtomicReference<(() -> Unit)?>(null)
    private val backendForceRef = AtomicReference<(() -> Unit)?>(null)
    private val launchThreadRef = AtomicReference<Thread?>(null)
    private val launchErrorRef = AtomicReference<Throwable?>(null)

    /* ============================================================
     * User callbacks
     * ============================================================ */

    protected var onReady: (App<C>) -> Unit = {}
    protected var onUpdate: (App<C>, delta: Float) -> Unit = { _, _ -> }
    protected var onResize: (App<C>, width: Int, height: Int) -> Unit = { _, _, _ -> }
    protected var onExit: (App<C>) -> Unit = {}
    protected var onInputs: (InputManager) -> Unit = {}

    /* ============================================================
     * Builder hooks
     * ============================================================ */

    protected var managerBuilder: ManagersRegistry.() -> Unit = {}
    protected var sceneManagerBuilder: SceneManager.() -> Unit = {}

    /* ============================================================
     * Public handle
     * ============================================================ */

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

    /* ============================================================
     * Extension hooks
     * ============================================================ */

    /**
     * Hook for subclasses to provide additional managers before setup.
     *
     * Do not include [InjectionManager], [SceneManager], or [ScreenManager] here.
     * Those are owned by [App].
     */
    protected open fun collectManagers(): List<Manager> = emptyList()

    /**
     * Hook for subclasses to configure the shared [SceneManager] before setup.
     */
    protected open fun configureSceneManager(sceneManager: SceneManager) = Unit

    /**
     * Hook called after managers and screens are ready.
     */
    protected open fun afterReady() = Unit

    /**
     * Hook called before the user update callback and screen frame update.
     */
    protected open fun beforeUpdate(delta: Float) = Unit

    /**
     * Hook called after the user resize callback.
     */
    protected open fun afterResize(width: Int, height: Int) = Unit

    /**
     * Hook called before teardown begins.
     */
    protected open fun beforeExit() = Unit

    /**
     * Platform-specific app entrypoint.
     */
    protected abstract fun internalLaunch(config: C, vararg args: String)

    /* ============================================================
     * Platform lifecycle
     * ============================================================ */

    /**
     * Called by the platform runtime when the app is actually starting.
     *
     * Final in behavior even if not marked final: subclasses should customize via hooks.
     */
    fun ready() {
        CanopyLogging.init(
            CanopyLogging.Config(
                engineVersion = CanopyBuildInfo.projectVersion
            )
        )

        val backendName = this::class.simpleName ?: "unknown"
        LogContext.with("backend" to backendName) {
            EngineLogs.lifecycle.info { "Booting Canopy..." }

            sceneManager.apply(sceneManagerBuilder)
            configureSceneManager(sceneManager)

            ManagersRegistry.withScope {
                managerBuilder()
                collectManagers().forEach(::register)
                +InjectionManager()
                +sceneManager
                +screenManager
            }

            screenRegistry.setup()

            if (ManagersRegistry.has(InputManager::class)) {
                onInputs(manager())
            }

            onReady(this@App)
            afterReady()

            startedLatch.countDown()

            EngineLogs.lifecycle.info("event" to "app.launch.init") {
                "Application started."
            }
        }
    }

    /**
     * Called by the platform runtime every frame/tick.
     */
    fun update(delta: Float) {
        frame++

        LogContext.with("frame" to frame) {
            beforeUpdate(delta)
            onUpdate(this@App, delta)
        }

        screenManager.frame(delta)
    }

    /**
     * Called by the platform runtime when the surface changes size.
     */
    fun resize(width: Int, height: Int) {
        sceneManager.resize(width, height)
        screenManager.resize(width, height)

        onResize(this, width, height)
        afterResize(width, height)

        EngineLogs.lifecycle.debug(
            "event" to "app.resize",
            "width" to width,
            "height" to height
        ) { "Screen resized." }
    }

    /**
     * Called by the platform runtime on shutdown.
     */
    fun exit() {
        try {
            EngineLogs.lifecycle.info("event" to "app.dispose") { "Disposing app" }

            beforeExit()
            ManagersRegistry.teardown()
            CanopyLogging.end(reason = "normal")
        } catch (t: Throwable) {
            CanopyLogging.end(reason = "crash", t = t)
            throw t
        } finally {
            try {
                onExit(this)
            } finally {
                markFinished()
            }
        }
    }

    /* ============================================================
     * Launch control
     * ============================================================ */

    fun launch(vararg args: String) {
        internalLaunch(config, *args)
    }

    fun launchAsync(threadName: String = "canopy-app", vararg args: String): AppHandle {
        val runnable = {
            try {
                internalLaunch(config, *args)
            } catch (t: Throwable) {
                launchErrorRef.set(t)
                startedLatch.countDown()
                markFinished()
                throw t
            }
        }

        val thread = Thread(runnable, threadName).apply {
            isDaemon = false
        }

        launchThreadRef.set(thread)
        thread.start()

        startedLatch.await()
        launchErrorRef.get()?.let { throw it }

        return handle
    }

    fun installBackendHandle(requestExit: () -> Unit, forceClose: (() -> Unit)? = null) {
        backendExitRef.set(requestExit)
        backendForceRef.set(forceClose ?: requestExit)
    }

    /* ============================================================
     * Configuration DSL
     * ============================================================ */

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

    fun inputs(vararg mappings: Pair<String, List<InputBind>>) {
        onInputs = { manager -> manager.mapActions(*mappings) }
    }

    fun sceneManager(handler: SceneManager.() -> Unit) {
        sceneManagerBuilder = handler
    }

    fun managers(handler: ManagersRegistry.() -> Unit) {
        managerBuilder = handler
    }

    /* ============================================================
     * Internals
     * ============================================================ */

    private fun markFinished() {
        if (finished.compareAndSet(false, true)) {
            finishedLatch.countDown()
        }
    }
}
