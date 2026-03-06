package io.canopy.engine.logging

import java.util.concurrent.atomic.AtomicReference
import io.canopy.engine.logging.util.withTemporaryMdcContext

/**
 * Logging context backed by MDC semantics.
 *
 * Design:
 * - Global context: long-lived values for the whole run/session
 *   (e.g. runId, engineVersion, backend).
 * - Scoped context: temporary values applied around a block
 *   (e.g. scene, nodePath, frame).
 * - Event fields: NOT managed here. Those are attached by the logger under the "fields" MDC key.
 *
 * Notes:
 * - Global context is stored outside MDC so it does not depend on thread-local lifecycle.
 * - Scoped context is applied to MDC only for the duration of a block.
 * - Values are normalized to strings because MDC is string-based.
 */
object LogContext {

    private val global = AtomicReference<Map<String, String>>(emptyMap())

    /**
     * Replaces the entire global context.
     */
    fun setGlobal(vararg fields: Pair<String, Any?>) {
        global.set(normalize(fields.toMap()))
    }

    /**
     * Adds or overrides entries in the global context.
     */
    fun updateGlobal(vararg fields: Pair<String, Any?>) {
        global.set(global.get() + normalize(fields.toMap()))
    }

    /**
     * Removes selected keys from global context.
     */
    fun removeGlobal(vararg keys: String) {
        if (keys.isEmpty()) return
        global.set(global.get() - keys.toSet())
    }

    /**
     * Clears all global context.
     */
    fun clearGlobal() {
        global.set(emptyMap())
    }

    /**
     * Snapshot used by the logger right before emitting a log event.
     */
    internal fun globalMdcSnapshot(): Map<String, String> = global.get()

    /**
     * Applies temporary scoped MDC fields for the duration of [block].
     *
     * Scoped fields override existing MDC values while the block runs,
     * then previous values are restored.
     */
    fun <T> with(vararg fields: Pair<String, Any?>, block: () -> T): T =
        withTemporaryMdcContext(normalize(fields.toMap()), block)

    private fun normalize(fields: Map<String, Any?>): Map<String, String> = buildMap(fields.size) {
        fields.forEach { (key, value) ->
            if (value != null) put(key, value.toString())
        }
    }
}
