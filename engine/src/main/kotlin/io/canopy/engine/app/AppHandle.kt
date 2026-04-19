package io.canopy.engine.app

import kotlin.time.Duration

interface AppHandle {
    fun requestExit()
    fun forceClose()

    suspend fun join()
    suspend fun join(timeout: Duration): Boolean

    suspend fun awaitStarted()
    suspend fun awaitStarted(timeout: Duration): Boolean
}
