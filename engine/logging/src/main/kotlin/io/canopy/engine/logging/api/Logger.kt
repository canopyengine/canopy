package io.canopy.engine.logging.api

interface Logger {
    fun isTraceEnabled(): Boolean
    fun isDebugEnabled(): Boolean
    fun isInfoEnabled(): Boolean
    fun isWarnEnabled(): Boolean
    fun isErrorEnabled(): Boolean

    fun log(level: LogLevel, t: Throwable? = null, fields: Map<String, Any?> = emptyMap(), msg: () -> String)

    fun trace(t: Throwable? = null, fields: Map<String, Any?> = emptyMap(), msg: () -> String) =
        log(LogLevel.TRACE, t, fields, msg)

    fun debug(t: Throwable? = null, fields: Map<String, Any?> = emptyMap(), msg: () -> String) =
        log(LogLevel.DEBUG, t, fields, msg)

    fun info(t: Throwable? = null, fields: Map<String, Any?> = emptyMap(), msg: () -> String) =
        log(LogLevel.INFO, t, fields, msg)

    fun warn(t: Throwable? = null, fields: Map<String, Any?> = emptyMap(), msg: () -> String) =
        log(LogLevel.WARN, t, fields, msg)

    fun error(t: Throwable? = null, fields: Map<String, Any?> = emptyMap(), msg: () -> String) =
        log(LogLevel.ERROR, t, fields, msg)
}
