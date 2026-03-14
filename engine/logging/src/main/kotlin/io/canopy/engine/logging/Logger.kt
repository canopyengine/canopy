package io.canopy.engine.logging

/**
 * Core logging abstraction used by the engine.
 *
 * Implementations of this interface provide the bridge between the engine's
 * logging API and a concrete logging backend (e.g. SLF4J).
 *
 * Design goals:
 * - Keep the engine independent of a specific logging framework
 * - Support structured logging through key/value fields
 * - Avoid unnecessary allocations via lazy message evaluation
 *
 * Implementations are responsible for mapping these calls to the underlying
 * logging system.
 */
interface Logger {

    /**
     * Indicates whether TRACE level logging is enabled.
     */
    fun isTraceEnabled(): Boolean

    /**
     * Indicates whether DEBUG level logging is enabled.
     */
    fun isDebugEnabled(): Boolean

    /**
     * Indicates whether INFO level logging is enabled.
     */
    fun isInfoEnabled(): Boolean

    /**
     * Indicates whether WARN level logging is enabled.
     */
    fun isWarnEnabled(): Boolean

    /**
     * Indicates whether ERROR level logging is enabled.
     */
    fun isErrorEnabled(): Boolean

    /**
     * Core logging function used by all convenience methods.
     *
     * @param level log severity
     * @param t optional throwable associated with the log entry
     * @param fields structured key/value pairs attached to the log entry
     * @param msg lazy message supplier to avoid unnecessary string construction
     *
     * Implementations should:
     * - Check whether the level is enabled
     * - Attach the structured fields if supported
     * - Evaluate [msg] only when the log will actually be emitted
     */
    fun log(level: LogLevel, t: Throwable? = null, vararg fields: Pair<String, Any?>, msg: () -> String)

    /* ---------- TRACE ---------- */

    fun trace(t: Throwable? = null, vararg fields: Pair<String, Any?>, msg: () -> String) =
        log(LogLevel.TRACE, t, fields = fields, msg)

    fun trace(vararg fields: Pair<String, Any?>, msg: () -> String) = log(LogLevel.TRACE, null, fields = fields, msg)

    /* ---------- DEBUG ---------- */

    fun debug(t: Throwable? = null, vararg fields: Pair<String, Any?>, msg: () -> String) =
        log(LogLevel.DEBUG, t, fields = fields, msg)

    fun debug(vararg fields: Pair<String, Any?>, msg: () -> String) = log(LogLevel.DEBUG, null, fields = fields, msg)

    /* ---------- INFO ---------- */

    fun info(t: Throwable? = null, vararg fields: Pair<String, Any?>, msg: () -> String) =
        log(LogLevel.INFO, t, fields = fields, msg)

    fun info(vararg fields: Pair<String, Any?>, msg: () -> String) = log(LogLevel.INFO, null, fields = fields, msg)

    /* ---------- WARN ---------- */

    fun warn(t: Throwable? = null, vararg fields: Pair<String, Any?>, msg: () -> String) =
        log(LogLevel.WARN, t, fields = fields, msg)

    fun warn(vararg fields: Pair<String, Any?>, msg: () -> String) = log(LogLevel.WARN, null, fields = fields, msg)

    /* ---------- ERROR ---------- */

    fun error(t: Throwable? = null, vararg fields: Pair<String, Any?>, msg: () -> String) =
        log(LogLevel.ERROR, t, fields = fields, msg)

    fun error(vararg fields: Pair<String, Any?>, msg: () -> String) = log(LogLevel.ERROR, null, fields = fields, msg)
}
