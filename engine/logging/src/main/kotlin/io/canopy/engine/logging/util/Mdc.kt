package io.canopy.engine.logging.util

import org.slf4j.MDC

/**
 * Executes a block of code with temporary MDC (Mapped Diagnostic Context) values.
 *
 * This utility is useful when you want logs produced inside a block to include
 * additional contextual fields (e.g., requestId, userId, correlationId).
 *
 * Behavior:
 * - Saves the current MDC values for the provided keys
 * - Applies the new values
 * - Executes the provided block
 * - Restores the previous MDC state after execution (even if an exception occurs)
 *
 * If a value in [fields] is `null`, the corresponding MDC key is removed.
 *
 * Example:
 * ```
 * withTemporaryMdcContext(mapOf("requestId" to request.id)) {
 *     logger.info("Processing request")
 * }
 * ```
 *
 * @param fields Key-value pairs to temporarily insert into the MDC
 * @param block The code to execute with the provided MDC context
 * @return The result returned by [block]
 */
internal inline fun <T> withTemporaryMdcContext(fields: Map<String, Any?>, block: () -> T): T {
    // If there are no fields to apply, execute the block immediately
    if (fields.isEmpty()) return block()

    // Store previous MDC values so they can be restored later
    val previousValues = HashMap<String, String?>(fields.size)

    try {
        fields.forEach { (key, value) ->
            previousValues[key] = MDC.get(key)

            if (value == null) {
                MDC.remove(key)
            } else {
                MDC.put(key, value.toString())
            }
        }

        // Execute the wrapped block with the temporary MDC values
        return block()
    } finally {
        // Restore the original MDC state
        previousValues.forEach { (key, previousValue) ->
            if (previousValue == null) {
                MDC.remove(key)
            } else {
                MDC.put(key, previousValue)
            }
        }
    }
}
