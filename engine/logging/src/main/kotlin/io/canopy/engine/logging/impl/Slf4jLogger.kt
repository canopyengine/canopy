package io.canopy.engine.logging.impl

import io.canopy.engine.logging.api.LogContext
import io.canopy.engine.logging.api.LogLevel
import io.canopy.engine.logging.api.Logger
import io.canopy.engine.logging.api.withMdc
import net.logstash.logback.argument.StructuredArguments.entries
import org.slf4j.Logger as Slf4j

class Slf4jLogger(private val delegate: Slf4j) : Logger {
    override fun isTraceEnabled(): Boolean = delegate.isTraceEnabled
    override fun isDebugEnabled(): Boolean = delegate.isDebugEnabled
    override fun isInfoEnabled(): Boolean = delegate.isInfoEnabled
    override fun isWarnEnabled(): Boolean = delegate.isWarnEnabled
    override fun isErrorEnabled(): Boolean = delegate.isErrorEnabled

    override fun log(level: LogLevel, t: Throwable?, fields: Map<String, Any?>, msg: () -> String) {
        val enabled = when (level) {
            LogLevel.TRACE -> delegate.isTraceEnabled
            LogLevel.DEBUG -> delegate.isDebugEnabled
            LogLevel.INFO -> delegate.isInfoEnabled
            LogLevel.WARN -> delegate.isWarnEnabled
            LogLevel.ERROR -> delegate.isErrorEnabled
        }
        if (!enabled) return

        val message = msg()

        // ✅ Only global context goes here (strings ok: runId, engineVersion).
        // ✅ Scoped context (frame/nodePath) is already in MDC if the caller used LogContext.with(...)
        withMdc(LogContext.globalMdcSnapshot()) {
            val structured = if (fields.isEmpty()) null else entries(fields)

            when (level) {
                LogLevel.TRACE -> logTrace(message, structured, t)
                LogLevel.DEBUG -> logDebug(message, structured, t)
                LogLevel.INFO -> logInfo(message, structured, t)
                LogLevel.WARN -> logWarn(message, structured, t)
                LogLevel.ERROR -> logError(message, structured, t)
            }
        }
    }

    private fun logTrace(message: String, structured: Any?, t: Throwable?) {
        when {
            structured != null && t != null -> delegate.trace(message, structured, t)
            structured != null -> delegate.trace(message, structured)
            t != null -> delegate.trace(message, t)
            else -> delegate.trace(message)
        }
    }

    private fun logDebug(message: String, structured: Any?, t: Throwable?) {
        when {
            structured != null && t != null -> delegate.debug(message, structured, t)
            structured != null -> delegate.debug(message, structured)
            t != null -> delegate.debug(message, t)
            else -> delegate.debug(message)
        }
    }

    private fun logInfo(message: String, structured: Any?, t: Throwable?) {
        when {
            structured != null && t != null -> delegate.info(message, structured, t)
            structured != null -> delegate.info(message, structured)
            t != null -> delegate.info(message, t)
            else -> delegate.info(message)
        }
    }

    private fun logWarn(message: String, structured: Any?, t: Throwable?) {
        when {
            structured != null && t != null -> delegate.warn(message, structured, t)
            structured != null -> delegate.warn(message, structured)
            t != null -> delegate.warn(message, t)
            else -> delegate.warn(message)
        }
    }

    private fun logError(message: String, structured: Any?, t: Throwable?) {
        when {
            structured != null && t != null -> delegate.error(message, structured, t)
            structured != null -> delegate.error(message, structured)
            t != null -> delegate.error(message, t)
            else -> delegate.error(message)
        }
    }
}
