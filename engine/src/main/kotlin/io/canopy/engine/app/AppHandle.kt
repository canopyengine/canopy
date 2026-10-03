package io.canopy.engine.app

import kotlin.time.Duration

/** Controls application shutdown and awaits startup or teardown without blocking a coroutine thread. */
interface AppHandle {
    /** Requests graceful shutdown using the backend callback, or interrupts the launch thread. */
    fun requestExit()

    /** Invokes backend force-close behavior; without a backend callback this can halt the JVM. */
    fun forceClose()

    /** Waits for teardown; propagates a failed lifecycle completion. */
    suspend fun join()

    /** Returns true when teardown completes; false on timeout or a failed wait. */
    suspend fun join(timeout: Duration): Boolean

    /** Waits for initialization; propagates startup failure. */
    suspend fun awaitStarted()

    /** Returns true when initialization completes; false on timeout or a failed wait. */
    suspend fun awaitStarted(timeout: Duration): Boolean
}
