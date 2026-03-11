package io.canopy.engine.logging

import java.util.concurrent.atomic.AtomicReference
import io.canopy.engine.logging.util.withTemporaryMdcContext

/**
 * Logging context backed by MDC (Mapped Diagnostic Context).
 *
 * MDC is best used for *context that should automatically appear on many log lines*,
 * typically via the logging pattern or encoder configuration.
 *
 * We split context into two categories:
 *
 * - Global context:
 *   Session-wide fields that should exist for the entire engine run
 *   (e.g. runId, engineVersion).
 *
 * - Scoped context:
 *   Short-lived fields applied only while executing a block
 *   (e.g. frame, nodePath, scene).
 *
 * Important:
 * - Do NOT put per-log-call structured fields into MDC.
 *   MDC stores values as strings and is thread-local; pushing arbitrary fields into MDC
 *   makes JSON logs less structured and can create hard-to-debug leakage across calls.
 *
 * Per-log-call fields should be attached by the logger implementation using the backend’s
 * structured logging mechanism (e.g. Logstash StructuredArguments).
 */
object LogContext {

    /**
     * Snapshot of global fields. Stored separately (not in MDC) so that:
     * - they can be applied consistently by the logging backend
     * - we avoid thread-local lifecycle issues for session-wide values
     */
    private val global = AtomicReference<Map<String, Any?>>(emptyMap())

    /**
     * Replaces the global context entirely.
     *
     * Use this during initialization when establishing session identity.
     */
    fun setGlobal(vararg fields: Pair<String, Any?>) {
        global.set(fields.toMap())
    }

    /**
     * Adds/overrides entries in the global context.
     *
     * Existing keys are overwritten with the new values.
     */
    fun updateGlobal(vararg fields: Pair<String, Any?>) {
        global.set(global.get() + fields.toMap())
    }

    /**
     * Returns the current global context snapshot.
     *
     * This is intended for logger backends/adapters (e.g. SLF4J) to apply global fields
     * to MDC right before emitting a log line.
     */
    internal fun globalMdcSnapshot(): Map<String, Any?> = global.get()

    /**
     * Executes [block] with additional scoped MDC fields.
     *
     * These fields are applied only for the duration of the block and are always restored,
     * even if [block] throws.
     */
    fun <T> with(vararg fields: Pair<String, Any?>, block: () -> T): T = withTemporaryMdcContext(fields.toMap(), block)
}
