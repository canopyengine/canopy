package io.canopy.engine.logging.slf4j

import io.canopy.engine.logging.LogContext
import io.canopy.engine.logging.LogLevel
import io.canopy.engine.logging.Logger
import io.canopy.engine.logging.util.withTemporaryMdcContext
import org.slf4j.Logger as Slf4j
import org.slf4j.MDC

internal class Slf4jLogger(private val delegate: Slf4j) : Logger {

    override fun isTraceEnabled(): Boolean = delegate.isTraceEnabled
    override fun isDebugEnabled(): Boolean = delegate.isDebugEnabled
    override fun isInfoEnabled(): Boolean = delegate.isInfoEnabled
    override fun isWarnEnabled(): Boolean = delegate.isWarnEnabled
    override fun isErrorEnabled(): Boolean = delegate.isErrorEnabled

    override fun log(level: LogLevel, t: Throwable?, vararg fields: Pair<String, Any?>, msg: () -> String) {
        val slf4jLevel = org.slf4j.event.Level.valueOf(level.name)
        if (!delegate.isEnabledForLevel(slf4jLevel)) return

        val baseMessage = msg()
        val formattedFields = fields.joinToString(", ") { (key, value) -> "$key=${value?.toString() ?: "null"}" }
        val message = if (fields.isEmpty()) baseMessage else "[$formattedFields] $baseMessage"

        val mergedMdc = LinkedHashMap<String, Any?>()

        // Preserve currently scoped MDC first
        MDC.getCopyOfContextMap()?.let { mergedMdc.putAll(it) }

        // Fill in global defaults without overriding scoped values
        LogContext.globalMdcSnapshot().forEach { (key, value) ->
            mergedMdc.putIfAbsent(key, value)
        }

        // Put per-event fields into one dedicated MDC entry
        if (fields.isNotEmpty()) {
            mergedMdc["fields"] = formattedFields
        }

        withTemporaryMdcContext(mergedMdc) {
            delegate.atLevel(slf4jLevel).setCause(t).log(message)
        }
    }
}
