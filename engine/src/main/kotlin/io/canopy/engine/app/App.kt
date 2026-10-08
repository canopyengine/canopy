package io.canopy.engine.app

import kotlin.time.Duration
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import io.canopy.engine.core.CanopyBuildInfo
import io.canopy.engine.core.CleanupFailures
import io.canopy.engine.core.managers.InjectionManager
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.input.InputFocus
import io.canopy.engine.logging.EngineLogs
import io.canopy.engine.logging.LogContext
import io.canopy.engine.logging.LoggingPolicy
import io.canopy.engine.logging.LoggingSession
import io.canopy.engine.ui.UiManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/**
 * Shared application lifecycle and configuration, driven by a platform through [engineLoop].
 * Failed startup rolls back the manager scope and runs shutdown hooks. Teardown attempts all stages,
 * preserving the first runtime failure with later cleanup failures suppressed before completing [handle].
 * Hosts must call [EngineLoop.reportFailure] before exit for failures outside loop dispatch.
 */
abstract class App<C : AppConfig> protected constructor() {
    /* ============================================================
     * Configuration
     * ============================================================ */
    private var _config: C? = null
    private var loggingPolicy: LoggingPolicy? = null

    @Volatile
    private var loggingSession: LoggingSession? = null
    protected val config: C
        get() = _config ?: defaultConfig()

    /** Returns the platform configuration used when no configuration has been supplied. */
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
    ).also { it.validateExit = ManagersRegistry::checkCanExit }

    /* ============================================================
     * Builder hooks
     * ============================================================ */
    protected var managerBuilder: ManagersRegistry.() -> Unit = {}

    /* ============================================================
     * Public handle
     * ============================================================ */

    /** Shutdown controls and lifecycle completion signals for this application. */
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
        } catch (_: CancellationException) {
            currentCoroutineContext().ensureActive()
            false
        } catch (_: Throwable) {
            currentCoroutineContext().ensureActive()
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
        } catch (_: CancellationException) {
            currentCoroutineContext().ensureActive()
            false
        } catch (_: Throwable) {
            currentCoroutineContext().ensureActive()
            false
        }
    }

    /* ============================================================
     * Hooks
     * ============================================================ */

    /** Platform logging default; core applications use host-owned logging. */
    protected open fun defaultLoggingPolicy(): LoggingPolicy = LoggingPolicy.Host

    protected open fun afterEnter() = Unit
    protected open fun beforeUpdate(delta: Float) = Unit
    protected open fun afterResize(width: Int, height: Int) = Unit
    protected open fun beforeExit() = Unit

    protected open fun provideManagers(): List<Manager> = emptyList()
    protected open fun SceneManager.configureSceneManager() = Unit

    /**
     * Drives the platform lifecycle. Report uncaught host failures through [EngineLoop.reportFailure]
     * before finally calling [EngineLoop.exit]; failure after stopped completion cannot replace its result.
     * A host may handle an intentional interrupt as graceful shutdown; unhandled interruption or
     * cancellation is a failed launch.
     */
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
        var ownsManagerScope = false
        try {
            loggingSession = (loggingPolicy ?: defaultLoggingPolicy()).start(CanopyBuildInfo.projectVersion)

            val backendName = this::class.simpleName ?: "unknown"
            withLoggingContext {
                LogContext.with("backend" to backendName) {
                    EngineLogs.lifecycle.info { "Booting Canopy..." }

                    var sceneManager: SceneManager? = null
                    ownsManagerScope = true
                    ManagersRegistry.withScope {
                        provideManagers().forEach(::register)
                        +InjectionManager()
                        +InputFocus()
                        +ScreenManager()
                        +SceneManager().also {
                            sceneManager = it
                            it.pauseState = { isPaused }
                            it.configureSceneManager()
                        }
                        +UiManager()
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
            }
        } catch (t: Throwable) {
            onStarted.safeFail(t)
            teardown(t, ownsManagerScope)
        }
    }

    private fun performUpdate(delta: Float) = withLoggingContext {
        updateSequence++
        if (!isPaused) gameplayFrames.incrementAndGet()

        LogContext.with("frame" to updateSequence) {
            val gameplayDelta = if (isPaused) 0f else delta
            beforeUpdate(gameplayDelta)
            onUpdate(this@App, gameplayDelta)
        }

        ManagersRegistry.update(delta, isPaused)
    }

    private fun performPhysicsUpdate(delta: Float) = withLoggingContext {
        if (!isPaused) onPhysicsUpdate(this@App, delta)
        ManagersRegistry.physicsUpdate(delta, isPaused)
    }

    private fun performResize(width: Int, height: Int) = withLoggingContext {
        ManagersRegistry.resize(width, height)

        onResize(this, width, height)
        afterResize(width, height)

        EngineLogs.lifecycle.debug(
            "event" to "app.resize",
            "width" to width,
            "height" to height
        ) { "Screen resized." }
    }

    private fun performExit(): Unit = teardown(engineLoop.failure)

    /** Attempts every cleanup stage; failed context setup falls back to host context for that stage. */
    private fun teardown(initialFailure: Throwable? = null, ownsManagerScope: Boolean = true) {
        val failures = CleanupFailures(initialFailure)

        fun scopedAttempt(block: () -> Unit) {
            var invoked = false
            failures.attempt {
                withLoggingContext {
                    invoked = true
                    block()
                }
            }
            // A custom session may fail before entering its scope; cleanup must still run.
            if (!invoked) failures.attempt(block)
        }

        scopedAttempt { EngineLogs.lifecycle.info("event" to "app.dispose") { "Disposing app" } }
        scopedAttempt { beforeExit() }
        if (ownsManagerScope) scopedAttempt { ManagersRegistry.exit() }
        scopedAttempt {
            loggingSession?.end(
                reason = if (failures.failure ==
                    null
                ) {
                    "normal"
                } else {
                    "crash"
                },
                failure = failures.failure
            )
        }
        scopedAttempt { onExit(this) }
        val session = loggingSession
        loggingSession = null
        failures.attempt { session?.close() }
        val error = failures.failure
        if (error == null) {
            onStopped.safeComplete()
        } else {
            onStopped.safeFail(error)
            throw error
        }
    }

    /* ============================================================
     * Launch
     * ============================================================ */

    /** Launches the platform on the calling thread; blocking behavior depends on the backend. */
    fun launch(vararg args: String) {
        launchHost(args)
    }

    /** Launches the platform on a non-daemon thread and returns lifecycle controls. */
    fun launchAsync(threadName: String = "canopy-app", vararg args: String): AppHandle {
        val thread = Thread({
            try {
                launchHost(args)
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

    private fun launchHost(args: Array<out String>) {
        try {
            internalLaunch(config, *args)
        } catch (error: Throwable) {
            fail(error)
        }
    }

    /**
     * Stops a failed host, completes pending lifecycle signals and rethrows the first runtime failure.
     * If an earlier failure was retained, [error] is suppressed on it. Call this on
     * the host lifecycle thread when a backend callback crashes and may not receive a later exit.
     * Cleanup is attempted exactly once. Hosts using a finally exit must report failure before it;
     * an already completed stopped signal cannot be replaced.
     */
    fun fail(error: Throwable): Nothing {
        engineLoop.checkHostFailureAllowed()
        try {
            engineLoop.exit(error)
        } catch (cleanup: Throwable) {
            val primary = engineLoop.failure ?: error
            if (cleanup !== primary && primary.suppressed.none { it === cleanup }) primary.addSuppressed(cleanup)
        }
        val primary = engineLoop.failure ?: error
        onStarted.safeFail(primary)
        onStopped.safeFail(primary)
        throw primary
    }

    /** Installs backend shutdown callbacks; a missing force-close callback uses requestExit. */
    fun installBackendHandle(requestExit: () -> Unit, forceClose: (() -> Unit)? = null) {
        backendExitRef.set(requestExit)
        backendForceRef.set(forceClose ?: requestExit)
    }

    /* ============================================================
     * DSL
     * ============================================================ */

    /**
     * Selects the logging policy for entry; call before launch or [enter].
     * This is application lifecycle configuration, independent of platform [AppConfig].
     * Existing loggers and providers are unchanged.
     */
    fun logging(policy: LoggingPolicy) {
        check(!onStarted.isCompleted && loggingSession == null) { "Configure logging before application entry" }
        loggingPolicy = policy
    }

    /**
     * Runs work with the active application's logging context on the calling thread.
     * Context is restored afterward; background work must opt in explicitly. Before entry or after close, uses host context.
     */
    fun <T> withLoggingContext(block: () -> T): T {
        val session = loggingSession
        return if (session == null) block() else session.withContext(block)
    }

    /** Replaces the configuration used for subsequent launch and lifecycle setup. */
    fun config(newConfig: C) {
        _config = newConfig
    }

    /** Replaces the callback invoked after manager initialization. */
    fun onEnter(handler: App<C>.() -> Unit) {
        onEnter = handler
    }

    /** Replaces the frame callback; delta is in seconds and is zero while paused. */
    fun onUpdate(handler: App<C>.(Float) -> Unit) {
        onUpdate = handler
    }

    /** Replaces the callback invoked for each unpaused fixed physics step, with delta in seconds. */
    fun onPhysicsUpdate(handler: App<C>.(Float) -> Unit) {
        onPhysicsUpdate = handler
    }

    /** Replaces the callback invoked after managers receive the new width and height. */
    fun onResize(handler: App<C>.(Int, Int) -> Unit) {
        onResize = handler
    }

    /** Replaces the callback invoked during final application teardown. */
    fun onExit(handler: App<C>.() -> Unit) {
        onExit = handler
    }

    /** Replaces the manager registration block run during application initialization. */
    fun managers(handler: ManagersRegistry.() -> Unit) {
        managerBuilder = handler
    }

    /* ============================================================
     * Internals
     * ============================================================ */

    private fun CompletableDeferred<Unit>.safeComplete() {
        if (!isCompleted) complete(Unit)
    }

    private fun CompletableDeferred<Unit>.safeFail(t: Throwable) {
        if (!isCompleted) completeExceptionally(t)
    }
}
