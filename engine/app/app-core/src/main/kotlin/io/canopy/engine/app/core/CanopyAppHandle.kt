package io.canopy.engine.app.core

import java.util.concurrent.TimeUnit

/**
 * Handle returned when launching a Canopy app asynchronously.
 *
 * This object allows external callers (tests, launchers, tools) to:
 *
 * - Request a graceful shutdown of the application
 * - Force-close the application if it becomes unresponsive
 * - Wait for the application to start or finish
 *
 * Backend implementations provide the actual behavior via the injected
 * callback functions.
 *
 * Typical usage:
 *
 * ```
 * val handle = app.launchAsync()
 *
 * handle.awaitStarted(5, TimeUnit.SECONDS)
 *
 * // ... interact with the app ...
 *
 * handle.requestExit()
 * handle.join()
 * ```
 *
 * This class implements [AutoCloseable] so it can be used in `use {}` blocks:
 *
 * ```
 * app.launchAsync().use { handle ->
 *     handle.awaitStarted(5, TimeUnit.SECONDS)
 * }
 * ```
 */
open class CanopyAppHandle(
    /** Called when a graceful shutdown is requested. */
    private val onRequestExit: () -> Unit,

    /** Called when the app must be forcefully terminated. */
    private val onForceClose: () -> Unit = onRequestExit,

    /**
     * Waits for the application to exit.
     *
     * Returns `true` if the app finished before the timeout.
     */
    private val onJoin: (timeout: Long, unit: TimeUnit) -> Boolean = { _, _ -> true },

    /**
     * Waits for the application to finish booting.
     *
     * Returns `true` if startup completed before the timeout.
     */
    private val onAwaitStarted: (timeout: Long, unit: TimeUnit) -> Boolean = { _, _ -> true },
) : AutoCloseable {

    /**
     * Requests a graceful application shutdown.
     *
     * Backends should interpret this as:
     * - closing the window
     * - stopping the render loop
     * - allowing cleanup to run normally
     */
    fun requestExit() = onRequestExit()

    /**
     * Immediately terminates the application.
     *
     * This should only be used as a last resort if graceful shutdown fails.
     */
    fun forceClose() = onForceClose()

    /**
     * Blocks indefinitely until the application exits.
     */
    fun join() {
        onJoin(Long.MAX_VALUE, TimeUnit.DAYS)
    }

    /**
     * Blocks until the application exits or the timeout expires.
     *
     * @return `true` if the application exited before the timeout
     */
    fun join(timeout: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): Boolean = onJoin(timeout, unit)

    /**
     * Allows this handle to be used with `use {}` blocks.
     * Closing the handle requests a graceful exit.
     */
    override fun close() = requestExit()

    /**
     * Waits until the application has fully started.
     *
     * Useful when launching asynchronously, and you need to ensure the
     * engine has finished initialization before interacting with it.
     *
     * @return `true` if the app started before the timeout
     */
    fun awaitStarted(timeout: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): Boolean = onAwaitStarted(timeout, unit)
}
