package io.canopy.engine.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import io.canopy.engine.logging.slf4j.Slf4jLogger
import org.slf4j.LoggerFactory

class ProviderTimingTests {
    private class RecordingLogger : Logger {
        val messages = mutableListOf<String>()
        override fun isTraceEnabled() = true
        override fun isDebugEnabled() = true
        override fun isInfoEnabled() = true
        override fun isWarnEnabled() = true
        override fun isErrorEnabled() = true
        override fun log(level: LogLevel, t: Throwable?, vararg fields: Pair<String, Any?>, msg: () -> String) {
            messages += msg()
        }
    }

    @Test
    fun `provider installation affects future loggers and leaves cached loggers unchanged`() {
        // Arrange
        val cachedEngineLogger = EngineLogs.lifecycle
        val first = RecordingLogger()
        val second = RecordingLogger()
        try {
            CanopyLogs.setProvider { first }
            val retained = logger("host.before")

            // Act
            retained.info { "before" }
            CanopyLogs.setProvider { second }
            retained.info { "retained" }
            logger("host.after").info { "after" }

            // Assert
            assertSame(cachedEngineLogger, EngineLogs.lifecycle)
            assertEquals(listOf("before", "retained"), first.messages)
            assertEquals(listOf("after"), second.messages)
        } finally {
            CanopyLogs.setProvider { name -> Slf4jLogger(LoggerFactory.getLogger(name)) }
        }
    }
}
