package io.canopy.engine.logging.api

import java.util.concurrent.atomic.AtomicReference

/**
 * Context = metadata attached automatically via MDC.
 *
 * - Global context: session-wide fields (runId, engineVersion).
 * - Scoped context: temporary fields for a block (frame, nodePath, scene).
 *
 * NOTE:
 * Per-log-call "fields" should NOT go into MDC, otherwise everything becomes strings in JSON.
 * Per-call fields should be attached using StructuredArguments in the logger implementation.
 */
object LogContext {
    private val global = AtomicReference<Map<String, Any?>>(emptyMap())

    fun setGlobal(vararg fields: Pair<String, Any?>) {
        global.set(fields.toMap())
    }

    fun updateGlobal(vararg fields: Pair<String, Any?>) {
        global.set(global.get() + fields.toMap())
    }

    /** Used by logger backend to apply global MDC fields. */
    internal fun globalMdcSnapshot(): Map<String, Any?> = global.get()

    /** Scoped MDC fields (thread-local) for a block. */
    fun <T> with(vararg fields: Pair<String, Any?>, block: () -> T): T = withMdc(fields.toMap(), block)
}
