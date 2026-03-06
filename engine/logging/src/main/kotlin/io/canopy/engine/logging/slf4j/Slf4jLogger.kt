package io.canopy.engine.logging.slf4j

import io.canopy.engine.logging.LogContext
import io.canopy.engine.logging.LogLevel
import io.canopy.engine.logging.core.Logger
import io.canopy.engine.logging.util.withTemporaryMdcContext
import net.logstash.logback.argument.StructuredArguments.entries
import org.slf4j.Logger as Slf4j

/**
 * SLF4J-backed implementation of the engine [Logger].
 *
 * Responsibilities:
 * - Perform level checks before doing any work (avoids allocations)
 * - Convert engine "structured fields" into Logstash Logback arguments
 * - Ensure *global* engine context is present in MDC for all log lines
 *
 * MDC vs structured fields:
 * - MDC is used for contextual data that should automatically appear on every log line
 *   (and may be included by the log pattern), e.g. runId, engineVersion.
 * - Structured fields are per-log-entry key/value pairs passed explicitly to the backend.
 *
 * Note:
 * Scoped context (like frame/nodePath) should already be in MDC when callers use
 * `LogContext.with(...)` or similar scoping utilities.
 */
class Slf4jLogger(private val delegate: Slf4j) : Logger {

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
    private fun formatFieldsForHumans(fields: Array<out Pair<String, Any?>>): String {
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
        val humanFields = formatFieldsForHumans(fields)
        val messageForPatterns = "$humanFields $baseMessage"

        withTemporaryMdcContext(LogContext.globalMdcSnapshot()) {
            val structuredArgs =
                fields.takeIf {
                    it.isNotEmpty()
                }?.let { entries(mapOf(*it)) }

            emit(level, messageForPatterns, structuredArgs, t)
        }
    }

    private fun isEnabled(level: LogLevel): Boolean = when (level) {
        LogLevel.TRACE -> delegate.isTraceEnabled
        LogLevel.DEBUG -> delegate.isDebugEnabled
        LogLevel.INFO -> delegate.isInfoEnabled
        LogLevel.WARN -> delegate.isWarnEnabled
        LogLevel.ERROR -> delegate.isErrorEnabled
    }

    /**
     * Emits the log statement to SLF4J, handling combinations of:
     * - structured arguments present/absent
     * - throwable present/absent
     *
     * We keep this logic centralized to avoid duplication across each level.
     */
    private fun emit(level: LogLevel, message: String, structured: Any?, t: Throwable?) {
        when (level) {
            LogLevel.TRACE -> when {
                structured != null && t != null -> delegate.trace(message, structured, t)
                structured != null -> delegate.trace(message, structured)
                t != null -> delegate.trace(message, t)
                else -> delegate.trace(message)
            }

            LogLevel.DEBUG -> when {
                structured != null && t != null -> delegate.debug(message, structured, t)
                structured != null -> delegate.debug(message, structured)
                t != null -> delegate.debug(message, t)
                else -> delegate.debug(message)
            }

            LogLevel.INFO -> when {
                structured != null && t != null -> delegate.info(message, structured, t)
                structured != null -> delegate.info(message, structured)
                t != null -> delegate.info(message, t)
                else -> delegate.info(message)
            }

            LogLevel.WARN -> when {
                structured != null && t != null -> delegate.warn(message, structured, t)
                structured != null -> delegate.warn(message, structured)
                t != null -> delegate.warn(message, t)
                else -> delegate.warn(message)
            }

            LogLevel.ERROR -> when {
                structured != null && t != null -> delegate.error(message, structured, t)
                structured != null -> delegate.error(message, structured)
                t != null -> delegate.error(message, t)
                else -> delegate.error(message)
            }
        }
    }
}
