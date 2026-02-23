package io.canopy.engine.logging.api

import kotlin.collections.iterator
import org.slf4j.MDC

internal inline fun <T> withMdc(fields: Map<String, Any?>, block: () -> T): T {
    if (fields.isEmpty()) return block()

    val old = HashMap<String, String?>(fields.size)
    try {
        for ((k, v) in fields) {
            old[k] = MDC.get(k)
            if (v == null) MDC.remove(k) else MDC.put(k, v.toString())
        }
        return block()
    } finally {
        for ((k, prev) in old) {
            if (prev == null) MDC.remove(k) else MDC.put(k, prev)
        }
    }
}
