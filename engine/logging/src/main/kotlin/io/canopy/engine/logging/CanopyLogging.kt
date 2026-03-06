package io.canopy.engine.logging

import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.CoreConstants
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.pattern.DynamicConverter
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.core.util.FileSize
import io.canopy.engine.logging.converters.ContextConverter
import io.canopy.engine.logging.converters.FieldsConverter
import io.canopy.engine.logging.util.ConsoleBanner
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CanopyLogging {

    data class Config(
        val baseLogDir: Path = Path.of(".canopy").resolve("logs"),
        val runId: String = defaultRunFolderName(),
        val engineVersion: String = "unknown",

        val consoleLevel: LogLevel = LogLevel.INFO,
        val bannerMode: ConsoleBanner.Mode = ConsoleBanner.Mode.GRADIENT,

        val engineJsonLevel: LogLevel = LogLevel.DEBUG,
        val engineLogLevel: LogLevel = LogLevel.INFO,
        val userLogLevel: LogLevel = LogLevel.INFO,

        val maxHistoryDays: Int = 7,
        val maxFileSize: String = "10MB",
        val totalSizeCap: String = "200MB",

        val engineLoggerPrefix: String = "canopy",
    )

    fun defaultRunFolderName(now: ZonedDateTime = ZonedDateTime.now()): String =
        "canopy-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm").format(now)

    private val startedAt = AtomicReference<Instant?>(null)

    fun init(config: Config = Config()) {
        ConsoleBanner.print(config.engineVersion, config.bannerMode)

        val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
        context.reset()
        registerConverters(context)

        val runDir = config.baseLogDir.resolve(config.runId)
        if (!runDir.exists()) runDir.createDirectories()

        LogContext.setGlobal(
            "runId" to config.runId,
            "engineVersion" to config.engineVersion
        )

        val consoleAppender = ConsoleAppender<ILoggingEvent>().apply {
            this.context = context
            name = "CONSOLE"
            encoder = buildConsoleEncoder(context)
            isWithJansi = true

            addFilter(denyEngineFilter(config.engineLoggerPrefix).apply { start() })

            addFilter(
                ThresholdFilter().apply {
                    setLevel(config.consoleLevel.name)
                    start()
                }
            )

            addFilter(
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

            start()
        }

        val engineLogAppender = RollingFileAppender<ILoggingEvent>().apply {
            this.context = context
            name = "ENGINE_LOG"
            file = runDir.resolve("engine.log").toString()

            encoder = buildFileEncoder(context)

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

        val userLogAppender = RollingFileAppender<ILoggingEvent>().apply {
            this.context = context
            name = "USER_LOG"
            file = runDir.resolve("user.log").toString()

            encoder = buildFileEncoder(context)

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

        context.getLogger(Logger.ROOT_LOGGER_NAME).apply {
            level = Level.TRACE
            addAppender(consoleAppender)
            addAppender(userLogAppender)
        }

        context.getLogger(config.engineLoggerPrefix).apply {
            isAdditive = true
            level = Level.TRACE
            addAppender(engineLogAppender)
        }

        startedAt.set(Instant.now())

        Logs.get("canopy.engine.session").info(
            "event" to "session.start",
            "schema" to "canopy-log-v1",
            "startedAt" to startedAt.get().toString(),
            "runId" to config.runId,
            "engineVersion" to config.engineVersion,
            "runDir" to runDir.toString()
        ) { "Session start" }

        Logs.get("canopy.bootstrap.logging").info(
            "event" to "logging.init",
            "runDir" to runDir.toString()
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
                "event" to "session.end",
                "reason" to reason,
                "endedAt" to now.toString(),
                "durationMs" to durationMs
            ) { "Session end" }
        } else {
            sessionLog.info(
                "event" to "session.end",
                "reason" to reason,
                "endedAt" to now.toString(),
                "durationMs" to durationMs
            ) { "Session end" }
        }
    }

    fun defaultBaseLogDir(baseDir: Path = Path.of(".canopy")): Path = baseDir.resolve("logs")

    fun defaultRunId(): String = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(ZonedDateTime.now())

    private fun registerConverters(context: LoggerContext) {
        @Suppress("UNCHECKED_CAST")
        val supplierRegistry =
            (
                context.getObject(CoreConstants.PATTERN_RULE_REGISTRY_FOR_SUPPLIERS)
                    as? MutableMap<String, Supplier<DynamicConverter<*>>>
                )
                ?: mutableMapOf<String, Supplier<DynamicConverter<*>>>().also {
                    context.putObject(CoreConstants.PATTERN_RULE_REGISTRY_FOR_SUPPLIERS, it)
                }

        supplierRegistry["ctx"] = Supplier { ContextConverter() }
        supplierRegistry["fields"] = Supplier { FieldsConverter() }

        @Suppress("UNCHECKED_CAST")
        val legacyRegistry =
            (context.getObject(CoreConstants.PATTERN_RULE_REGISTRY) as? MutableMap<String, String>)
                ?: mutableMapOf<String, String>().also {
                    context.putObject(CoreConstants.PATTERN_RULE_REGISTRY, it)
                }

        legacyRegistry["ctx"] = ContextConverter::class.java.canonicalName
        legacyRegistry["fields"] = FieldsConverter::class.java.canonicalName
    }

    fun buildConsoleEncoder(context: LoggerContext): PatternLayoutEncoder = PatternLayoutEncoder().apply {
        this.context = context
        pattern =
            "%d{yy.MM.dd.HH:mm:ss.SSS} " +
            "%highlight(%-5level) " +
            "%boldCyan(%logger{1}) " +
            "%white([run=%X{runId}]) " +
            "%ctx{color=true} " +
            "%fields{color=true} " +
            "%msg%ex{short}%n"
        start()
    }

    fun buildFileEncoder(context: LoggerContext): PatternLayoutEncoder = PatternLayoutEncoder().apply {
        this.context = context
        pattern =
            "%d{yy.MM.dd.HH:mm:ss.SSS} %-5level %logger{36} " +
            "[run=%X{runId}] " +
            "%ctx %fields %msg%ex{short}%n"
        start()
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
