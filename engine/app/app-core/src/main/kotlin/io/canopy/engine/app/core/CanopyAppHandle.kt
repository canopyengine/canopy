package io.canopy.engine.app.core

import java.util.concurrent.TimeUnit

open class CanopyAppHandle(
    private val onRequestExit: () -> Unit,
    private val onForceClose: () -> Unit = onRequestExit,
    private val onJoin: (timeout: Long, unit: TimeUnit) -> Boolean = { _, _ -> true },
    private val onAwaitStarted: (timeout: Long, unit: TimeUnit) -> Boolean = { _, _ -> true },
) : AutoCloseable {

    fun requestExit() = onRequestExit()

    fun forceClose() = onForceClose()

    fun join() {
        onJoin(Long.MAX_VALUE, TimeUnit.DAYS)
    }

    fun join(timeout: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): Boolean = onJoin(timeout, unit)

    override fun close() = requestExit()

    fun awaitStarted(timeout: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): Boolean = onAwaitStarted(timeout, unit)
}
