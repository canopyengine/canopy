package io.canopy.engine.logging.util

import org.slf4j.MDC

internal inline fun <T> withTemporaryMdcContext(fields: Map<String, Any?>, block: () -> T): T {
    if (fields.isEmpty()) return block()

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

        return block()
    } finally {
        previousValues.forEach { (key, previousValue) ->
            if (previousValue == null) {
                MDC.remove(key)
            } else {
                MDC.put(key, previousValue)
            }
        }
    }
}
