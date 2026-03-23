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

    /**
     * Formats structured fields for display in log messages.
     *
     * Fields are formatted as key=value pairs without ANSI colors.
     * Colors are applied by the Logback encoder configuration (Console vs File).
     * This ensures colors appear in the terminal but not in log files.
     */
    private fun formatFields(fields: Array<out Pair<String, Any?>>): String {
        if (fields.isEmpty()) return ""

        val formatted = fields.joinToString(separator = ", ") { (k, v) ->
            val valueStr = v?.toString() ?: "null"
            "$k=$valueStr"
        }

        return "[$formatted]"
    }

    override fun log(level: LogLevel, t: Throwable?, vararg fields: Pair<String, Any?>, msg: () -> String) {
        if (!isEnabled(level)) return

        val baseMessage = msg()
        val fieldsPrefix = formatFields(fields)
        val message = if (fieldsPrefix.isEmpty()) baseMessage else "$fieldsPrefix $baseMessage"

        val mergedMdc = LinkedHashMap<String, Any?>()

        // Preserve currently scoped MDC first
        MDC.getCopyOfContextMap()?.let { mergedMdc.putAll(it) }

        // Fill in global defaults without overriding scoped values
        LogContext.globalMdcSnapshot().forEach { (key, value) ->
            mergedMdc.putIfAbsent(key, value)
        }

        // Put per-event fields into one dedicated MDC entry
        if (fields.isNotEmpty()) {
            mergedMdc["fields"] = fields.joinToString(", ") { (k, v) ->
                "$k=${v?.toString() ?: "null"}"
            }
        }

        withTemporaryMdcContext(mergedMdc) {
            emit(level, message, t)
        }
    }

    private fun isEnabled(level: LogLevel): Boolean = when (level) {
        LogLevel.TRACE -> delegate.isTraceEnabled
        LogLevel.DEBUG -> delegate.isDebugEnabled
        LogLevel.INFO -> delegate.isInfoEnabled
        LogLevel.WARN -> delegate.isWarnEnabled
        LogLevel.ERROR -> delegate.isErrorEnabled
    }

    private fun emit(level: LogLevel, message: String, t: Throwable?) {
        when (level) {
            LogLevel.TRACE -> if (t != null) delegate.trace(message, t) else delegate.trace(message)
            LogLevel.DEBUG -> if (t != null) delegate.debug(message, t) else delegate.debug(message)
            LogLevel.INFO -> if (t != null) delegate.info(message, t) else delegate.info(message)
            LogLevel.WARN -> if (t != null) delegate.warn(message, t) else delegate.warn(message)
            LogLevel.ERROR -> if (t != null) delegate.error(message, t) else delegate.error(message)
        }
    }
}
