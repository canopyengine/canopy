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
        val logDir: Path,
        val runId: String,
        val engineVersion: String = "unknown",

        val consoleLevel: LogLevel = LogLevel.INFO,
        val engineFileLevel: LogLevel = LogLevel.DEBUG,

        val maxHistoryDays: Int = 7,
        val maxFileSize: String = "10MB",
        val totalSizeCap: String = "200MB",

        /** If true, root logs (including user/app) will be written to the engine file too. */
        val includeUserLogsInFile: Boolean = false,
    )

    private val startedAt = AtomicReference<Instant?>(null)

    /**
     * Call once, as early as possible (before any EngineLogs/app logs).
     * If Logback isn't the active backend, it's a no-op.
     */
    fun init(config: Config) {
        val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return

        // Reset defaults to avoid duplicate appenders on re-init.
        context.reset()

        if (!config.logDir.exists()) config.logDir.createDirectories()

        // Global structured context for the whole session
        LogContext.setGlobal(
            "runId" to config.runId,
            "engineVersion" to config.engineVersion
        )

        // ----------------------------
        // Console appender (readable)
        // ----------------------------
        val consoleEncoder = buildConsoleEncoder(context)

        val consoleAppender = ConsoleAppender<ILoggingEvent>().apply {
            this.context = context
            name = "CONSOLE"
            encoder = consoleEncoder
            isWithJansi = true
            addFilter(
                ThresholdFilter().apply {
                    setLevel(config.consoleLevel.name)
                    start()
                }
            )
            start()
        }

        // Optional: drop noisy external logs from console (example: OpenAL/ALSOFT)
        // Keep it simple; edit as needed.
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
        // Engine JSONL file appender
        // ----------------------------
        val engineFileAppender = RollingFileAppender<ILoggingEvent>().apply {
            this.context = context
            name = "ENGINE_FILE"
            file = config.logDir.resolve("canopy-engine-${config.runId}.jsonl").toString()

            encoder = LogstashEncoder().apply {
                this.context = context
                // includes MDC by default; that’s where runId/engineVersion go
                start()
            }
        }

        val rollingPolicy = SizeAndTimeBasedRollingPolicy<ILoggingEvent>().apply {
            this.context = context
            setParent(engineFileAppender)
            fileNamePattern =
                config.logDir.resolve("canopy-engine-%d{yyyy-MM-dd}.${config.runId}.%i.jsonl.gz").toString()
            maxHistory = config.maxHistoryDays
            setMaxFileSize(FileSize.valueOf(config.maxFileSize))
            setTotalSizeCap(FileSize.valueOf(config.totalSizeCap))
            start()
        }

        engineFileAppender.rollingPolicy = rollingPolicy

        engineFileAppender.addFilter(
            ThresholdFilter().apply {
                setLevel(config.engineFileLevel.name)
                start()
            }
        )

        engineFileAppender.start()

        // ----------------------------
        // Root logger routing
        // ----------------------------
        val root = context.getLogger(Logger.ROOT_LOGGER_NAME).apply {
            level = Level.TRACE // let appenders filter; simpler mental model
            addAppender(consoleAppender)
        }

        // Everything under canopy.engine.* goes to engine file (and also to console via root)
        context.getLogger("canopy.engine").apply {
            isAdditive = true
            level = Level.TRACE
            addAppender(engineFileAppender)
        }

        // If you want app/user logs included in the same file, attach file appender to root.
        if (config.includeUserLogsInFile) {
            root.addAppender(engineFileAppender)
        }

        // ----------------------------
        // Session "header" (JSONL-safe)
        // ----------------------------
        // This is your “header”: a dedicated first-class JSON event.
        // We log to a canopy.engine.* logger so it definitely lands in the engine JSONL file.
        startedAt.set(Instant.now())

        Logs.get("canopy.engine.session").info(
            fields = mapOf(
                "event" to "session.start",
                "schema" to "canopy-log-v1",
                "startedAt" to startedAt.get().toString(),
                "runId" to config.runId,
                "engineVersion" to config.engineVersion,
                "logDir" to config.logDir.toString()
            )
        ) { "Session start" }

        // Also emit a human-friendly line on console via bootstrap logger (optional).
        val bootLog = Logs.get("canopy.bootstrap.logging")
        bootLog.info(
            fields = mapOf(
                "event" to "logging.init",
                "logDir" to config.logDir.toString()
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

    fun defaultLogDir(baseDir: Path = Path.of(".canopy")): Path = baseDir.resolve("logs")

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
}
