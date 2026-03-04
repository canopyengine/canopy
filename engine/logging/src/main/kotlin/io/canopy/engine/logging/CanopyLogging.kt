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
import io.canopy.engine.logging.util.ConsoleBanner
import io.canopy.engine.logging.util.MdcExcludeConverter
import net.logstash.logback.encoder.LogstashEncoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Programmatic logging bootstrap for the Canopy engine.
 *
 * Why this exists:
 * - The engine should be able to start with a working, consistent logging setup
 *   without relying on an external `logback.xml`.
 * - We route "engine-origin" logs separately from "user-origin" logs so users get
 *   clean output and diagnostics remain available for debugging.
 *
 * Output layout (per run):
 * - Console: user logs only (engine logs are suppressed from console)
 * - engine.jsonl: structured JSON logs for the engine (machine readable)
 * - engine.log: readable engine logs (human friendly)
 * - user.log: readable user logs only (non-engine)
 *
 * Important:
 * - [init] is designed to be called once, as early as possible.
 * - If the active SLF4J backend is not Logback, this becomes a no-op (no reset).
 */
object CanopyLogging {

    /**
     * Logging configuration for a single engine run/session.
     *
     * Naming:
     * - runId becomes the directory name under [baseLogDir]
     * - engineLoggerPrefix defines what counts as "engine" (everything else is "user")
     */
    data class Config(
        val baseLogDir: Path = Path.of(".canopy").resolve("logs"),
        val runId: String = defaultRunFolderName(), // canopy-YYYY-MM-dd-HH-mm
        val engineVersion: String = "unknown",

        // Console output is intentionally user-focused (engine logs are filtered out)
        val consoleLevel: LogLevel = LogLevel.INFO,
        val bannerMode: ConsoleBanner.Mode = ConsoleBanner.Mode.GRADIENT,

        // Engine output files can be noisier than console
        val engineJsonLevel: LogLevel = LogLevel.DEBUG,
        val engineLogLevel: LogLevel = LogLevel.INFO,
        val userLogLevel: LogLevel = LogLevel.INFO,

        // Rolling policy controls
        val maxHistoryDays: Int = 7,
        val maxFileSize: String = "10MB",
        val totalSizeCap: String = "200MB",

        // Defines which logger names are considered "engine-origin"
        // e.g. "canopy" means anything starting with "canopy..." is engine
        val engineLoggerPrefix: String = "canopy",
    )

    /**
     * Default run folder name used on disk.
     *
     * Note:
     * - Avoid `HH:mm` because ':' is not a valid character in Windows filenames.
     */
    fun defaultRunFolderName(now: ZonedDateTime = ZonedDateTime.now()): String =
        "canopy-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm").format(now)

    /**
     * Tracks when [init] completed so we can compute session duration in [end].
     */
    private val startedAt = AtomicReference<Instant?>(null)

    /**
     * Initializes logging for the current run.
     *
     * Call this once, before any meaningful logging occurs.
     *
     * Implementation detail:
     * - If Logback is present (SLF4J backend is Logback), we reset the context and
     *   install our appenders programmatically.
     * - If not Logback, we return early and do not touch logging configuration.
     */
    fun init(config: Config) {
        // Print the startup banner early. This is cosmetic, but helps users confirm startup.
        ConsoleBanner.print(config.engineVersion, config.bannerMode)

        // Only proceed if the backend is Logback; otherwise we leave logging untouched.
        val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return

        // Reset the Logback context so we fully control appenders/levels for this run.
        context.reset()

        // Create per-run directory: <baseLogDir>/<runId>/
        val runDir = config.baseLogDir.resolve(config.runId)
        if (!runDir.exists()) runDir.createDirectories()

        // Global context goes into MDC so it is available on all log lines.
        // Scoped context (frame/nodePath, etc.) should be set by callers as needed.
        LogContext.setGlobal(
            "runId" to config.runId,
            "engineVersion" to config.engineVersion
        )

        /* ============================================================
         *  APPENDERS
         * ============================================================
         *
         * We attach appenders to:
         * - Root logger: console + user.log
         * - Engine namespace logger (config.engineLoggerPrefix): engine.jsonl + engine.log
         *
         * Filters ensure:
         * - Console receives user logs only
         * - user.log receives user logs only
         * - engine.* files receive engine logs only
         */

        // ----------------------------
        // Console appender (user logs only)
        // ----------------------------
        val consoleAppender = ConsoleAppender<ILoggingEvent>().apply {
            this.context = context
            name = "CONSOLE"
            encoder = buildConsoleEncoder(context)
            isWithJansi = true // enables ANSI colors when supported by the console

            // Prevent engine-origin logs from cluttering the console.
            addFilter(denyEngineFilter(config.engineLoggerPrefix).apply { start() })

            // Apply minimum console severity threshold.
            addFilter(
                ThresholdFilter().apply {
                    setLevel(config.consoleLevel.name)
                    start()
                }
            )
            start()
        }

        // Optional noise filter: suppress known noisy OpenAL logs.
        // Keep this close to the console appender to avoid affecting file logs.
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
        // Engine structured logs: engine.jsonl (machine readable)
        // ----------------------------
        val engineJsonAppender = RollingFileAppender<ILoggingEvent>().apply {
            this.context = context
            name = "ENGINE_JSONL"
            file = runDir.resolve("engine.jsonl").toString()

            // LogstashEncoder emits JSON per line (JSONL).
            encoder = LogstashEncoder().apply {
                this.context = context
                start()
            }

            // Accept engine-origin logs only.
            addFilter(engineOnlyFilter(config.engineLoggerPrefix).apply { start() })

            // Apply minimum severity threshold for JSON logs.
            addFilter(
                ThresholdFilter().apply {
                    setLevel(config.engineJsonLevel.name)
                    start()
                }
            )
        }

        // Roll policy: rotate daily and by size, gzip old files.
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
        // Engine readable logs: engine.log (human friendly)
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

            // Accept engine-origin logs only.
            addFilter(engineOnlyFilter(config.engineLoggerPrefix).apply { start() })

            // Apply minimum severity threshold for readable engine logs.
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
        // User logs: user.log (non-engine only)
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

            // Deny engine-origin logs so user.log stays clean.
            addFilter(denyEngineFilter(config.engineLoggerPrefix).apply { start() })

            // Apply minimum severity threshold for user.log.
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

        /* ============================================================
         *  LOGGER ROUTING
         * ============================================================
         *
         * Root logger:
         * - Receives everything, but appenders have filters.
         * - Console + user.log are attached here.
         *
         * Engine namespace logger (e.g. "canopy"):
         * - Receives engine-origin logs and attaches engine files.
         * - isAdditive=true means logs still propagate to root, but root appenders
         *   already deny engine logs via filters (so console/user.log remain clean).
         */

        val root = context.getLogger(Logger.ROOT_LOGGER_NAME).apply {
            level = Level.TRACE
            addAppender(consoleAppender)
            addAppender(userLogAppender)
        }

        context.getLogger(config.engineLoggerPrefix).apply {
            isAdditive = true
            level = Level.TRACE
            addAppender(engineJsonAppender)
            addAppender(engineLogAppender)
        }

        // Record session start time for duration computation in [end].
        startedAt.set(Instant.now())

        // Emit a session header event into engine logs.
        Logs.get("canopy.engine.session").info(
            "event" to "session.start",
            "schema" to "canopy-log-v1",
            "startedAt" to startedAt.get().toString(),
            "runId" to config.runId,
            "engineVersion" to config.engineVersion,
            "runDir" to runDir.toString()
        ) { "Session start" }

        // Emit a bootstrap event so it's easy to confirm logging configured correctly.
        Logs.get("canopy.bootstrap.logging").info(
            "event" to "logging.init",
            "runDir" to runDir.toString()
        ) { "Canopy logging initialized" }
    }

    /**
     * Emits the session end event.
     *
     * This does not shut down Logback; it only records the end-of-session marker.
     */
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

    /**
     * Default base log directory (<baseDir>/logs).
     */
    fun defaultBaseLogDir(baseDir: Path = Path.of(".canopy")): Path = baseDir.resolve("logs")

    /**
     * Alternative run id format used by some callers (kept for compatibility).
     */
    fun defaultRunId(): String = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(ZonedDateTime.now())

    /**
     * Builds the console encoder and registers any custom conversion words.
     *
     * We register `mdcx` so console output can show MDC keys *except* some noisy ones,
     * while still allowing specific keys to be displayed (e.g. runId).
     */
    fun buildConsoleEncoder(context: LoggerContext): PatternLayoutEncoder {
        // 1) New registry (logback 1.5.13+): conversionWord -> Supplier<DynamicConverter>
        @Suppress("UNCHECKED_CAST")
        val supplierRegistry =
            (
                context.getObject(CoreConstants.PATTERN_RULE_REGISTRY_FOR_SUPPLIERS)
                    as? MutableMap<String, Supplier<DynamicConverter<*>>>
                )
                ?: mutableMapOf<String, Supplier<DynamicConverter<*>>>().also {
                    context.putObject(CoreConstants.PATTERN_RULE_REGISTRY_FOR_SUPPLIERS, it)
                }

        supplierRegistry["mdcx"] = Supplier { MdcExcludeConverter() }

        // 2) Legacy registry (older logback): conversionWord -> FQCN (keep if you support < 1.5.13)
        @Suppress("UNCHECKED_CAST")
        val legacyRegistry =
            (context.getObject(CoreConstants.PATTERN_RULE_REGISTRY) as? MutableMap<String, String>)
                ?: mutableMapOf<String, String>().also {
                    context.putObject(CoreConstants.PATTERN_RULE_REGISTRY, it)
                }

        legacyRegistry["mdcx"] = MdcExcludeConverter::class.java.canonicalName

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

    /**
     * Filter that accepts only loggers that start with [prefix].
     * Used to route engine-origin logs into engine files.
     */
    private fun engineOnlyFilter(prefix: String) = object : Filter<ILoggingEvent>() {
        override fun decide(event: ILoggingEvent): FilterReply {
            val name = event.loggerName ?: return FilterReply.DENY
            return if (name.startsWith(prefix)) FilterReply.NEUTRAL else FilterReply.DENY
        }
    }

    /**
     * Filter that denies loggers that start with [prefix].
     * Used to keep console and user.log free of engine-origin logs.
     */
    private fun denyEngineFilter(prefix: String) = object : Filter<ILoggingEvent>() {
        override fun decide(event: ILoggingEvent): FilterReply {
            val name = event.loggerName ?: return FilterReply.NEUTRAL
            return if (name.startsWith(prefix)) FilterReply.DENY else FilterReply.NEUTRAL
        }
    }
}
