package io.canopy.engine.logging.api

interface Logger {
    fun isTraceEnabled(): Boolean
    fun isDebugEnabled(): Boolean
    fun isInfoEnabled(): Boolean
    fun isWarnEnabled(): Boolean
    fun isErrorEnabled(): Boolean

    fun log(level: LogLevel, t: Throwable? = null, vararg fields: Pair<String, Any?>, msg: () -> String)

    fun trace(t: Throwable? = null, vararg fields: Pair<String, Any?>, msg: () -> String) =
        log(LogLevel.TRACE, t, fields = fields, msg)

    fun trace(vararg fields: Pair<String, Any?>, msg: () -> String) = log(LogLevel.TRACE, null, fields = fields, msg)

    fun debug(t: Throwable? = null, vararg fields: Pair<String, Any?>, msg: () -> String) =
        log(LogLevel.DEBUG, t, fields = fields, msg)

    fun debug(vararg fields: Pair<String, Any?>, msg: () -> String) = log(LogLevel.DEBUG, null, fields = fields, msg)

    fun info(t: Throwable? = null, vararg fields: Pair<String, Any?>, msg: () -> String) =
        log(LogLevel.INFO, t, fields = fields, msg)

    fun info(vararg fields: Pair<String, Any?>, msg: () -> String) = log(LogLevel.INFO, null, fields = fields, msg)

    fun warn(t: Throwable? = null, vararg fields: Pair<String, Any?>, msg: () -> String) =
        log(LogLevel.WARN, t, fields = fields, msg)

    fun warn(vararg fields: Pair<String, Any?>, msg: () -> String) = log(LogLevel.WARN, null, fields = fields, msg)

    fun error(t: Throwable? = null, vararg fields: Pair<String, Any?>, msg: () -> String) =
        log(LogLevel.ERROR, t, fields = fields, msg)

    fun error(vararg fields: Pair<String, Any?>, msg: () -> String) = log(LogLevel.ERROR, null, fields = fields, msg)
}
