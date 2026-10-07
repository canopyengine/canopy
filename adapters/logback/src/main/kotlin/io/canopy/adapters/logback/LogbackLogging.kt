package io.canopy.adapters.logback

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.encoder.Encoder
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.core.util.FileSize
import io.canopy.engine.logging.LogContext
import io.canopy.engine.logging.LoggingPolicy
import io.canopy.engine.logging.LoggingSession
import io.canopy.engine.logging.engineLogger
import net.logstash.logback.encoder.LogstashEncoder
import org.slf4j.LoggerFactory

/**
 * Optional per-application Logback files and banner. Existing providers, appenders, levels and routing are preserved.
 * Only scoped SLF4J events reaching the engine or root logger are captured; host filters and non-additive descendants
 * may suppress delivery. Custom Canopy providers are unchanged and may route events elsewhere.
 * Start/end/close run on the application lifecycle thread; background context use does not delay shutdown.
 */
class LogbackLogging(private val config: Config = Config()) : LoggingPolicy {
    /** Managed output options; an explicit [runId] must identify a new directory beneath [baseLogDir]. */
    data class Config(
        val baseLogDir: Path = Path.of(".canopy", "logs"),
        val runId: String? = null,
        val banner: Boolean = true,
    )

    override fun start(engineVersion: String): LoggingSession {
        val context = LoggerFactory.getILoggerFactory() as? LoggerContext
            ?: error("Managed logging requires the SLF4J Logback backend; select LoggingPolicy.Host for other backends")
        val token = UUID.randomUUID().toString()
        val runId = config.runId ?: "${FOLDER_TIME.format(ZonedDateTime.now())}-$token"
        require(
            runId.isNotBlank() && runId != "." && runId != ".." && '/' !in runId && '\\' !in runId && ':' !in runId
        ) {
            "runId must be one directory name"
        }
        Files.createDirectories(config.baseLogDir)
        val directory = Files.createDirectory(config.baseLogDir.resolve(runId)).toAbsolutePath()
        val session = Session(context, directory, runId, token, engineVersion)
        try {
            session.open()
            session.withContext {
                if (config.banner) ConsoleBanner.print(engineVersion, ConsoleBanner.Mode.GRADIENT)
                engineLogger("session").info(
                    "event" to "session.start",
                    "schema" to "canopy-log-v1",
                    "startedAt" to session.startedAt.toString(),
                    "runId" to runId,
                    "engineVersion" to engineVersion,
                    "runDir" to directory.toString()
                ) { "Session start" }
            }
            return session
        } catch (failure: Throwable) {
            try {
                session.close()
            } catch (cleanup: Throwable) {
                if (cleanup !== failure) failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    private class Session(
        private val context: LoggerContext,
        private val directory: Path,
        private val runId: String,
        private val token: String,
        private val engineVersion: String,
    ) : LoggingSession {
        val startedAt: Instant = Instant.now()
        private val owned = mutableListOf<OwnedAppender>()
        private var ended = false

        @Volatile
        private var closed = false

        fun open() {
            for (engine in listOf(true, false)) {
                for (json in listOf(false, true)) addAppender(engine, json)
            }
        }

        private fun addAppender(engine: Boolean, json: Boolean) {
            val category = if (engine) "engine" else "app"
            val extension = if (json) "jsonl" else "log"
            val logger = context.getLogger(if (engine) ENGINE_NAMESPACE else org.slf4j.Logger.ROOT_LOGGER_NAME)
            val appender = RollingFileAppender<ILoggingEvent>()
            appender.context = context
            appender.name = "canopy-$token-$category-$extension"
            appender.file = directory.resolve("$category.$extension").toString()
            val encoder: Encoder<ILoggingEvent> = if (json) {
                LogstashEncoder().also { it.context = context }
            } else {
                PatternLayoutEncoder().also {
                    it.context = context
                    it.pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} %msg%n%ex"
                }
            }
            val rolling = SizeAndTimeBasedRollingPolicy<ILoggingEvent>().also {
                it.context = context
                it.setParent(appender)
                it.fileNamePattern = directory.resolve("$category.%d{yyyy-MM-dd}.%i.$extension").toString()
                it.setMaxFileSize(FileSize.valueOf("10MB"))
                it.maxHistory = 30
            }
            // Track all children before starting any so partial initialization can release them.
            owned += OwnedAppender(logger, appender, encoder, rolling)
            appender.addFilter(object : Filter<ILoggingEvent>() {
                override fun decide(event: ILoggingEvent): FilterReply {
                    val engineEvent = event.loggerName == ENGINE_NAMESPACE ||
                        event.loggerName.startsWith("$ENGINE_NAMESPACE.")
                    return if (event.mdcPropertyMap[SESSION_KEY] == token && engineEvent == engine) {
                        FilterReply.NEUTRAL
                    } else {
                        FilterReply.DENY
                    }
                }
            })
            appender.encoder = encoder
            appender.rollingPolicy = rolling
            appender.triggeringPolicy = rolling
            encoder.start()
            rolling.start()
            appender.start()
            check(encoder.isStarted && rolling.isStarted && appender.isStarted) {
                "Unable to start managed log file ${appender.file}"
            }
            logger.addAppender(appender)
        }

        override fun <T> withContext(block: () -> T): T = if (closed) {
            block()
        } else {
            LogContext.with(SESSION_KEY to token, "runId" to runId, "engineVersion" to engineVersion, block = block)
        }

        override fun end(reason: String, failure: Throwable?) {
            if (ended || closed) return
            ended = true
            withContext {
                val now = Instant.now()
                val fields = arrayOf(
                    "event" to "session.end",
                    "reason" to reason,
                    "endedAt" to now.toString(),
                    "durationMs" to Duration.between(startedAt, now).toMillis()
                )
                val log = engineLogger("session")
                if (failure == null) {
                    log.info(*fields) { "Session end" }
                } else {
                    log.error(failure, *fields) { "Session end" }
                }
            }
        }

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            var failure: Throwable? = null
            fun attempt(block: () -> Unit) {
                try {
                    block()
                } catch (error: Throwable) {
                    val first = failure
                    if (first == null) {
                        failure = error
                    } else if (first !== error) {
                        first.addSuppressed(error)
                    }
                }
            }
            for ((logger, appender, encoder, rolling) in owned.asReversed()) {
                attempt { logger.detachAppender(appender) }
                attempt { appender.stop() }
                attempt { rolling.stop() }
                attempt { encoder.stop() }
            }
            owned.clear()
            failure?.let { throw it }
        }
    }

    private data class OwnedAppender(
        val logger: ch.qos.logback.classic.Logger,
        val appender: RollingFileAppender<ILoggingEvent>,
        val encoder: Encoder<ILoggingEvent>,
        val rolling: SizeAndTimeBasedRollingPolicy<ILoggingEvent>,
    )

    private companion object {
        const val ENGINE_NAMESPACE = "io.canopy.engine"
        const val SESSION_KEY = "canopy.logging.session"
        val FOLDER_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
