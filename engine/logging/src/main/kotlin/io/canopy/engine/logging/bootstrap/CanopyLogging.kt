package io.canopy.engine.logging.bootstrap

import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.CoreConstants
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.core.util.FileSize
import io.canopy.engine.logging.api.LogContext
import io.canopy.engine.logging.api.LogLevel
import io.canopy.engine.logging.api.Logs
import net.logstash.logback.encoder.LogstashEncoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CanopyLogging {

    data class Config(
        val baseLogDir: Path = Path.of(".canopy").resolve("logs"),
        val runId: String = defaultRunFolderName(), // canopy-YYYY-MM-dd-HH-mm
        val engineVersion: String = "unknown",

        val consoleLevel: LogLevel = LogLevel.INFO,

        val engineJsonLevel: LogLevel = LogLevel.DEBUG,
        val engineLogLevel: LogLevel = LogLevel.INFO,
        val userLogLevel: LogLevel = LogLevel.INFO,

        val maxHistoryDays: Int = 7,
        val maxFileSize: String = "10MB",
        val totalSizeCap: String = "200MB",

        // What counts as "engine origin"
        val engineLoggerPrefix: String = "canopy",
    )

    fun defaultRunFolderName(now: ZonedDateTime = ZonedDateTime.now()): String =
        "canopy-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm").format(now)
// NOTE: HH:mm would break on Windows

    private val startedAt = AtomicReference<Instant?>(null)

    /**
     * Call once, as early as possible (before any EngineLogs/app logs).
     * If Logback isn't the active backend, it's a no-op.
     */
    fun init(config: Config) {
        val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
        context.reset()

        val runDir = config.baseLogDir.resolve(config.runId)
        if (!runDir.exists()) runDir.createDirectories()

        LogContext.setGlobal(
            "runId" to config.runId,
            "engineVersion" to config.engineVersion
        )

        // ----------------------------
        // Console appender (keep yours)
        // ----------------------------
        val consoleAppender = ConsoleAppender<ILoggingEvent>().apply {
            this.context = context
            name = "CONSOLE"
            encoder = buildConsoleEncoder(context)
            isWithJansi = true

            // Log user logs only
            addFilter(denyEngineFilter(config.engineLoggerPrefix).apply { start() })

            addFilter(
                ThresholdFilter().apply {
                    setLevel(config.consoleLevel.name)
                    start()
                }
            )
            start()
        }

        // Optional: keep your noisy OpenAL filter (unchanged)
        consoleAppender.addFilter(
            object : Filter<ILoggingEvent>() {
                override fun decide(event: ILoggingEvent): FilterReply {
                    val name = event.loggerName ?: return FilterReply.NEUTRAL
                    if (name.startsWith("org.lwjgl.openal") || event.formattedMessage.contains("[ALSOFT]")) {
                        return FilterReply.DENY
                    }
                    return FilterReply.NEUTRAL
                }
            }.apply { start() }
        )

        // ----------------------------
        // ENGINE: engine.jsonl
        // ----------------------------
        val engineJsonAppender = RollingFileAppender<ILoggingEvent>().apply {
            this.context = context
            name = "ENGINE_JSONL"
            file = runDir.resolve("engine.jsonl").toString()

            encoder = LogstashEncoder().apply {
                this.context = context
                start()
            }

            addFilter(engineOnlyFilter(config.engineLoggerPrefix).apply { start() })
            addFilter(
                ThresholdFilter().apply {
                    setLevel(config.engineJsonLevel.name)
                    start()
                }
            )
        }

        engineJsonAppender.rollingPolicy = SizeAndTimeBasedRollingPolicy<ILoggingEvent>().apply {
            this.context = context
            setParent(engineJsonAppender)
            fileNamePattern = runDir.resolve("engine.%d{yyyy-MM-dd}.%i.jsonl.gz").toString()
            maxHistory = config.maxHistoryDays
            setMaxFileSize(FileSize.valueOf(config.maxFileSize))
            setTotalSizeCap(FileSize.valueOf(config.totalSizeCap))
            start()
        }

        engineJsonAppender.start()

        // ----------------------------
        // ENGINE: engine.log (readable)
        // ----------------------------
        val engineLogAppender = RollingFileAppender<ILoggingEvent>().apply {
            this.context = context
            name = "ENGINE_LOG"
            file = runDir.resolve("engine.log").toString()

            encoder = PatternLayoutEncoder().apply {
                this.context = context
                pattern =
                    "%d{HH:mm:ss.SSS} %-5level %logger{36} " +
                    "[run=%X{runId}] %msg%ex{short}%n"
                start()
            }

            addFilter(engineOnlyFilter(config.engineLoggerPrefix).apply { start() })
            addFilter(
                ThresholdFilter().apply {
                    setLevel(config.engineLogLevel.name)
                    start()
                }
            )
        }

        engineLogAppender.rollingPolicy = SizeAndTimeBasedRollingPolicy<ILoggingEvent>().apply {
            this.context = context
            setParent(engineLogAppender)
            fileNamePattern = runDir.resolve("engine.%d{yyyy-MM-dd}.%i.log.gz").toString()
            maxHistory = config.maxHistoryDays
            setMaxFileSize(FileSize.valueOf(config.maxFileSize))
            setTotalSizeCap(FileSize.valueOf(config.totalSizeCap))
            start()
        }

        engineLogAppender.start()

        // ----------------------------
        // USER: user.log (readable, non-canopy)
        // ----------------------------
        val userLogAppender = RollingFileAppender<ILoggingEvent>().apply {
            this.context = context
            name = "USER_LOG"
            file = runDir.resolve("user.log").toString()

            encoder = PatternLayoutEncoder().apply {
                this.context = context
                pattern =
                    "%d{HH:mm:ss.SSS} %-5level %logger{36} " +
                    "[run=%X{runId}] %msg%ex{short}%n"
                start()
            }

            // deny canopy.* so user.log is truly user-origin
            addFilter(denyEngineFilter(config.engineLoggerPrefix).apply { start() })
            addFilter(
                ThresholdFilter().apply {
                    setLevel(config.userLogLevel.name)
                    start()
                }
            )
        }

        userLogAppender.rollingPolicy = SizeAndTimeBasedRollingPolicy<ILoggingEvent>().apply {
            this.context = context
            setParent(userLogAppender)
            fileNamePattern = runDir.resolve("user.%d{yyyy-MM-dd}.%i.log.gz").toString()
            maxHistory = config.maxHistoryDays
            setMaxFileSize(FileSize.valueOf(config.maxFileSize))
            setTotalSizeCap(FileSize.valueOf(config.totalSizeCap))
            start()
        }

        userLogAppender.start()

        // ----------------------------
        // Root routing
        // ----------------------------
        // Root: console + user.log (but user.log filter blocks canopy.*)
        val root = context.getLogger(Logger.ROOT_LOGGER_NAME).apply {
            level = Level.TRACE
            addAppender(consoleAppender)
            addAppender(userLogAppender)
        }

        // Engine namespace: add engine files
        context.getLogger(config.engineLoggerPrefix).apply {
            isAdditive = true // can be true or false; console filter already blocks engine from printing
            level = Level.TRACE
            addAppender(engineJsonAppender)
            addAppender(engineLogAppender)
        }

        // Session header logs (unchanged idea)
        startedAt.set(Instant.now())

        Logs.get("canopy.engine.session").info(
            fields = mapOf(
                "event" to "session.start",
                "schema" to "canopy-log-v1",
                "startedAt" to startedAt.get().toString(),
                "runId" to config.runId,
                "engineVersion" to config.engineVersion,
                "runDir" to runDir.toString()
            )
        ) { "Session start" }

        Logs.get("canopy.bootstrap.logging").info(
            fields = mapOf(
                "event" to "logging.init",
                "runDir" to runDir.toString()
            )
        ) { "Canopy logging initialized" }
    }

    fun end(reason: String = "normal", t: Throwable? = null) {
        val start = startedAt.get()
        val now = Instant.now()
        val durationMs = start?.let { Duration.between(it, now).toMillis() }

        val sessionLog = Logs.get("canopy.engine.session")
        if (t != null) {
            sessionLog.error(
                t = t,
                fields = mapOf(
                    "event" to "session.end",
                    "reason" to reason,
                    "endedAt" to now.toString(),
                    "durationMs" to durationMs
                )
            ) { "Session end" }
        } else {
            sessionLog.info(
                fields = mapOf(
                    "event" to "session.end",
                    "reason" to reason,
                    "endedAt" to now.toString(),
                    "durationMs" to durationMs
                )
            ) { "Session end" }
        }
    }

    fun defaultBaseLogDir(baseDir: Path = Path.of(".canopy")): Path = baseDir.resolve("logs")

    fun defaultRunId(): String = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(ZonedDateTime.now())

    fun buildConsoleEncoder(context: LoggerContext): PatternLayoutEncoder {
        // 1) Register conversion word -> converter class
        @Suppress("UNCHECKED_CAST")
        val registry = (context.getObject(CoreConstants.PATTERN_RULE_REGISTRY) as? MutableMap<String, String>)
            ?: mutableMapOf<String, String>().also {
                context.putObject(CoreConstants.PATTERN_RULE_REGISTRY, it)
            }

        registry["mdcx"] = "io.canopy.engine.logging.bootstrap.MdcExcludeConverter" // <-- your real FQCN

        // 2) Build encoder
        return PatternLayoutEncoder().apply {
            this.context = context
            pattern =
                "%d{HH:mm:ss.SSS} " +
                "%highlight(%-5level) " +
                "%boldCyan(%logger{1}) " +
                "%white([run=%X{runId}]) " +
                "%mdcx{runId,engineVersion} " +
                "%msg%ex{short}%n"
            start()
        }
    }

    private fun engineOnlyFilter(prefix: String) = object : Filter<ILoggingEvent>() {
        override fun decide(event: ILoggingEvent): FilterReply {
            val name = event.loggerName ?: return FilterReply.DENY
            return if (name.startsWith(prefix)) FilterReply.NEUTRAL else FilterReply.DENY
        }
    }

    private fun denyEngineFilter(prefix: String) = object : Filter<ILoggingEvent>() {
        override fun decide(event: ILoggingEvent): FilterReply {
            val name = event.loggerName ?: return FilterReply.NEUTRAL
            return if (name.startsWith(prefix)) FilterReply.DENY else FilterReply.NEUTRAL
        }
    }
}
