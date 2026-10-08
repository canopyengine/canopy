package io.canopy.engine.logging.slf4j

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.AppenderBase
import io.canopy.engine.logging.LogContext
import io.canopy.engine.logging.LogLevel
import org.slf4j.Logger
import org.slf4j.MDC

class Slf4jLoggerTests {
    private fun withContext(block: () -> Unit) {
        val mdc = MDC.getCopyOfContextMap()
        val global = LogContext.globalMdcSnapshot()
        try {
            MDC.clear()
            LogContext.clearGlobal()
            block()
        } finally {
            if (mdc == null) MDC.clear() else MDC.setContextMap(mdc)
            LogContext.setGlobal(*global.entries.map { it.key to it.value }.toTypedArray())
        }
    }

    @Test
    fun `all levels preserve literal messages and throwable identity`() {
        val context = LoggerContext()
        try {
            val events = mutableListOf<ILoggingEvent>()
            val appender = object : AppenderBase<ILoggingEvent>() {
                override fun append(eventObject: ILoggingEvent) {
                    events += eventObject
                }
            }.apply { start() }
            val delegate = context.getLogger("test").apply {
                level = Level.TRACE
                addAppender(appender)
            }
            val logger = Slf4jLogger(delegate)
            for (level in LogLevel.entries) {
                for (cause in listOf(null, IllegalStateException(level.name))) {
                    logger.log(level, cause) { "literal {} message" }
                    val event = events.last()
                    assertEquals(level.name, event.level.levelStr)
                    assertEquals("literal {} message", event.formattedMessage)
                    assertSame(cause, (event.throwableProxy as? ThrowableProxy)?.throwable)
                }
            }
            assertEquals(10, events.size)
        } finally {
            context.stop()
        }
    }

    @Test
    fun `disabled levels evaluate neither message nor structured fields`() {
        val context = LoggerContext()
        try {
            val logger = Slf4jLogger(context.getLogger("disabled").apply { level = Level.OFF })
            val field = object {
                override fun toString(): String = error("field evaluated")
            }
            for (level in LogLevel.entries) logger.log(level, null, "field" to field) { error("message evaluated") }
        } finally {
            context.stop()
        }
    }

    @Test
    fun `fields format once and temporary MDC restores after a legacy provider throws`() = withContext {
        val failure = IllegalArgumentException("provider")
        var observedMessage: String? = null
        var observedMdc: Map<String, String>? = null
        var observedCause: Throwable? = null
        val loggedCause = AssertionError("logged cause")
        val delegate = Proxy.newProxyInstance(Logger::class.java.classLoader, arrayOf(Logger::class.java)) {
                proxy,
                method,
                args,
            ->
            when {
                method.isDefault -> InvocationHandler.invokeDefault(proxy, method, *(args ?: emptyArray()))
                method.name.startsWith("is") && method.returnType == Boolean::class.javaPrimitiveType -> true
                method.name == "getName" -> "legacy"
                method.name == "info" -> {
                    observedMessage = args!![0] as String
                    observedCause = args.drop(1).flatMap { if (it is Array<*>) it.toList() else listOf(it) }
                        .filterIsInstance<Throwable>().lastOrNull()
                    observedMdc = MDC.getCopyOfContextMap()
                    throw failure
                }
                else -> error("Unexpected provider method: ${method.name}")
            }
        } as Logger
        val logger = Slf4jLogger(delegate)
        var formats = 0
        val value = object {
            override fun toString(): String = "value-${++formats}"
        }
        LogContext.setGlobal("same" to "global", "default" to "global-default", "fields" to "global-fields")
        MDC.put("outside", "retained")
        LogContext.with("same" to "outer", "fields" to "outer-fields") {
            LogContext.with("same" to "inner") {
                assertSame(
                    failure,
                    assertFailsWith<IllegalArgumentException> {
                        logger.log(LogLevel.INFO) { "without fields" }
                    }
                )
                assertEquals("without fields", observedMessage)
                assertEquals("outer-fields", observedMdc?.get("fields"))
                assertSame(
                    failure,
                    assertFailsWith<IllegalArgumentException> {
                        logger.log(LogLevel.INFO, loggedCause, "duplicate" to value, "duplicate" to null) { "message" }
                    }
                )
                assertEquals("inner", MDC.get("same"))
                assertEquals("outer-fields", MDC.get("fields"))
                assertEquals(null, MDC.get("default"))
            }
            assertEquals("outer", MDC.get("same"))
        }
        assertEquals(mapOf("outside" to "retained"), MDC.getCopyOfContextMap())
        assertSame(loggedCause, observedCause)
        assertEquals(1, formats)
        assertEquals("[duplicate=value-1, duplicate=null] message", observedMessage)
        assertEquals(
            mapOf(
                "outside" to "retained",
                "same" to "inner",
                "default" to "global-default",
                "fields" to "duplicate=value-1, duplicate=null"
            ),
            observedMdc
        )
    }
}
