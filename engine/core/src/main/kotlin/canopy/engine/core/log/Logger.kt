package canopy.engine.core.log

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Kotlin-friendly logger facade:
 * - lazy message lambdas
 * - optional Throwable
 * - no LibGDX dependency
 */
interface Log {
    fun trace(t: Throwable? = null, msg: () -> String)
    fun debug(t: Throwable? = null, msg: () -> String)
    fun info(t: Throwable? = null, msg: () -> String)
    fun warn(t: Throwable? = null, msg: () -> String)
    fun error(t: Throwable? = null, msg: () -> String)
}

private class Slf4jLog(private val delegate: Logger) : Log {
    override fun trace(t: Throwable?, msg: () -> String) {
        if (!delegate.isTraceEnabled) return
        if (t != null) delegate.trace(msg(), t) else delegate.trace(msg())
    }
    override fun debug(t: Throwable?, msg: () -> String) {
        if (!delegate.isDebugEnabled) return
        if (t != null) delegate.debug(msg(), t) else delegate.debug(msg())
    }
    override fun info(t: Throwable?, msg: () -> String) {
        if (!delegate.isInfoEnabled) return
        if (t != null) delegate.info(msg(), t) else delegate.info(msg())
    }
    override fun warn(t: Throwable?, msg: () -> String) {
        if (!delegate.isWarnEnabled) return
        if (t != null) delegate.warn(msg(), t) else delegate.warn(msg())
    }
    override fun error(t: Throwable?, msg: () -> String) {
        if (!delegate.isErrorEnabled) return
        if (t != null) delegate.error(msg(), t) else delegate.error(msg())
    }
}

/**
 * Global entrypoint (easy to replace later if you ever want).
 */
object Logs {
    fun get(name: String): Log = Slf4jLog(LoggerFactory.getLogger(name))
    inline fun <reified T : Any> of(): Log = get(T::class.java.name)
}

/** KTX-like helper: `private val log = logger<MyClass>()` */
inline fun <reified T : Any> logger(): Log = Logs.of<T>()
