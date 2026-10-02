package io.canopy.engine.app

import kotlin.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import io.canopy.engine.core.CanopyBuildInfo
import io.canopy.engine.core.managers.InjectionManager
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.logging.CanopyLogging
import io.canopy.engine.logging.EngineLogs
import io.canopy.engine.logging.LogContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

abstract class App<C : AppConfig> protected constructor() {
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

    private var updateSequence: Long = 0
    private val gameplayFrames = AtomicLong(0)

    @Volatile
    var isPaused: Boolean = false
        private set

    /** Number of app updates processed while the app was not paused. */
    val frameCount: Long
        get() = gameplayFrames.get()

    /** Pauses gameplay updates while allowing the app loop to continue handling input and rendering. */
    fun pause() {
        isPaused = true
    }

    /** Resumes gameplay updates. */
    fun resume() {
        isPaused = false
    }

    private val onStarted = CompletableDeferred<Unit>()
    private val onStopped = CompletableDeferred<Unit>()
    private val finished = AtomicBoolean(false)

    private val backendExitRef = AtomicReference<(() -> Unit)?>(null)
    private val backendForceRef = AtomicReference<(() -> Unit)?>(null)
    private val launchThreadRef = AtomicReference<Thread?>(null)

    /* ============================================================
     * User callbacks
     * ============================================================ */

    protected var onEnter: (App<C>) -> Unit = {}
    protected var onUpdate: (App<C>, delta: Float) -> Unit = { _, _ -> }
    protected var onPhysicsUpdate: (App<C>, delta: Float) -> Unit = { _, _ -> }
    protected var onResize: (App<C>, width: Int, height: Int) -> Unit = { _, _, _ -> }
    protected var onExit: (App<C>) -> Unit = {}

    /** Shared lifecycle coordinator used by platform drivers. */
    val engineLoop = EngineLoop(
        onEnter = ::performEnter,
        onUpdate = ::performUpdate,
        onPhysicsUpdate = ::performPhysicsUpdate,
        onResize = ::performResize,
        onExit = ::performExit,
        isPaused = { isPaused }
    )

    /* ============================================================
     * Builder hooks
     * ============================================================ */
    protected var managerBuilder: ManagersRegistry.() -> Unit = {}

    /* ============================================================
     * Public handle
     * ============================================================ */

    val handle: AppHandle = object : AppHandle {

        override fun requestExit() {
            val exit = backendExitRef.get()
            val thread = launchThreadRef.get()

            when {
                exit != null -> exit()
                thread != null -> thread.interrupt()
            }
        }

        override fun forceClose() {
            val force = backendForceRef.get()

            when {
                force != null -> force()
                else -> Runtime.getRuntime().halt(0)
            }
        }

        override suspend fun join() {
            onStopped.await()
        }

        override suspend fun join(timeout: Duration): Boolean = try {
            withTimeout(timeout) {
                onStopped.await()
                true
            }
        } catch (_: Exception) {
            false
        }

        override suspend fun awaitStarted() {
            onStarted.await()
        }

        override suspend fun awaitStarted(timeout: Duration): Boolean = try {
            withTimeout(timeout) {
                onStarted.await()
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /* ============================================================
     * Hooks
     * ============================================================ */

    protected open fun afterEnter() = Unit
    protected open fun beforeUpdate(delta: Float) = Unit
    protected open fun afterResize(width: Int, height: Int) = Unit
    protected open fun beforeExit() = Unit

    protected open fun provideManagers(): List<Manager> = emptyList()
    protected open fun SceneManager.configureSceneManager() = Unit

    protected abstract fun internalLaunch(config: C, vararg args: String)

    /* ============================================================
     * Lifecycle
     * ============================================================ */

    /** Compatibility wrapper; drivers should forward lifecycle events through [engineLoop]. */
    fun enter() = engineLoop.enter()

    fun update(delta: Float) = engineLoop.update(delta)

    fun physicsUpdate(delta: Float) = engineLoop.physicsUpdate(delta)

    fun resize(width: Int, height: Int) = engineLoop.resize(width, height)

    fun exit() = engineLoop.exit()

    private fun performEnter() {
        try {
            CanopyLogging.init(
                CanopyLogging.Config(
                    engineVersion = CanopyBuildInfo.projectVersion
                )
            )

            val backendName = this::class.simpleName ?: "unknown"
            LogContext.with("backend" to backendName) {
                EngineLogs.lifecycle.info { "Booting Canopy..." }

                var sceneManager: SceneManager? = null
                ManagersRegistry.withScope {
                    provideManagers().forEach(::register)
                    +InjectionManager()
                    +ScreenManager()
                    +SceneManager().also {
                        sceneManager = it
                        it.configureSceneManager()
                    }
                    managerBuilder()
                }
                sceneManager?.let { engineLoop.configurePhysicsStep(it.physicsStep) }

                onEnter(this@App)
                afterEnter()

                onStarted.safeComplete()

                EngineLogs.lifecycle.info("event" to "app.launch.init") {
                    "Application started."
                }
            }
        } catch (t: Throwable) {
            onStarted.safeFail(t)
            onStopped.safeFail(t)
            throw t
        }
    }

    private fun performUpdate(delta: Float) {
        updateSequence++
        if (!isPaused) gameplayFrames.incrementAndGet()

        LogContext.with("frame" to updateSequence) {
            beforeUpdate(delta)
            onUpdate(this@App, delta)
        }

        ManagersRegistry.update(delta)
    }

    private fun performPhysicsUpdate(delta: Float) {
        onPhysicsUpdate(this@App, delta)
        ManagersRegistry.physicsUpdate(delta)
    }

    private fun performResize(width: Int, height: Int) {
        ManagersRegistry.resize(width, height)

        onResize(this, width, height)
        afterResize(width, height)

        EngineLogs.lifecycle.debug(
            "event" to "app.resize",
            "width" to width,
            "height" to height
        ) { "Screen resized." }
    }

    private fun performExit() {
        try {
            EngineLogs.lifecycle.info("event" to "app.dispose") { "Disposing app" }

            beforeExit()
            ManagersRegistry.exit()
            CanopyLogging.end(reason = "normal")
        } catch (t: Throwable) {
            CanopyLogging.end(reason = "crash", t = t)
            onStopped.safeFail(t)
            throw t
        } finally {
            try {
                onExit(this)
            } finally {
                onStopped.safeComplete()
                markFinished()
            }
        }
    }

    /* ============================================================
     * Launch
     * ============================================================ */

    fun launch(vararg args: String) {
        internalLaunch(config, *args)
    }

    fun launchAsync(threadName: String = "canopy-app", vararg args: String): AppHandle {
        val thread = Thread({
            try {
                internalLaunch(config, *args)
            } catch (t: Throwable) {
                onStarted.safeFail(t)
                onStopped.safeFail(t)
                throw t
            }
        }, threadName).apply {
            isDaemon = false
        }

        launchThreadRef.set(thread)
        thread.start()

        return handle
    }

    fun installBackendHandle(requestExit: () -> Unit, forceClose: (() -> Unit)? = null) {
        backendExitRef.set(requestExit)
        backendForceRef.set(forceClose ?: requestExit)
    }

    /* ============================================================
     * DSL
     * ============================================================ */

    fun config(newConfig: C) {
        _config = newConfig
    }

    fun onEnter(handler: App<C>.() -> Unit) {
        onEnter = handler
    }

    fun onUpdate(handler: App<C>.(Float) -> Unit) {
        onUpdate = handler
    }

    fun onPhysicsUpdate(handler: App<C>.(Float) -> Unit) {
        onPhysicsUpdate = handler
    }

    fun onResize(handler: App<C>.(Int, Int) -> Unit) {
        onResize = handler
    }

    fun onExit(handler: App<C>.() -> Unit) {
        onExit = handler
    }

    fun managers(handler: ManagersRegistry.() -> Unit) {
        managerBuilder = handler
    }

    /* ============================================================
     * Internals
     * ============================================================ */

    private fun markFinished() {
        finished.compareAndSet(false, true)
    }

    private fun CompletableDeferred<Unit>.safeComplete() {
        if (!isCompleted) complete(Unit)
    }

    private fun CompletableDeferred<Unit>.safeFail(t: Throwable) {
        if (!isCompleted) completeExceptionally(t)
    }
}
