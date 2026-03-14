package io.canopy.engine.logging

import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import io.canopy.engine.logging.util.ConsoleBanner
import org.slf4j.LoggerFactory

/**
 * Programmatic logging bootstrap for the Canopy engine.
 *
 * Responsibilities:
 * - Creates per-run log directories
 * - Sets LOG_DIR system property used by canopy-logback.xml
 * - Reloads Logback configuration dynamically
 * - Prints startup banner
 * - Tracks session lifecycle
 */
object CanopyLogging {

    /**
     * Lazily created loggers.
     *
     * IMPORTANT:
     * These must not be initialized eagerly, otherwise SLF4J
     * may initialize Logback before LOG_DIR is set.
     */
    private val sessionLogger
        get() = engineLogger("session")

    private val bootstrapLogger
        get() = engineLogger("bootstrap")

    /**
     * Logging configuration for a single run/session.
     */
    data class Config(
        val baseLogDir: Path = Path.of(".canopy").resolve("logs"),
        val runId: String = defaultRunFolderName(),
        val engineVersion: String = "unknown",
        val bannerMode: ConsoleBanner.Mode = ConsoleBanner.Mode.GRADIENT,
    )

    /**
     * Default run folder name: YYYYMMDD-HHmmss
     */
    fun defaultRunFolderName(now: ZonedDateTime = ZonedDateTime.now()): String =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(now)

    private val startedAt = AtomicReference<Instant?>(null)

    /**
     * Initialize logging for this run.
     *
     * Must be called very early in application startup.
     */
    fun init(config: Config = Config()) {
        val runDir = config.baseLogDir.resolve(config.runId)

        if (!runDir.exists()) {
            runDir.createDirectories()
        }

        /*
         * Set system property BEFORE Logback config loads.
         * canopy-logback.xml uses ${LOG_DIR}.
         */
        System.setProperty("LOG_DIR", runDir.toAbsolutePath().toString())

        /*
         * Reload Logback configuration so the new LOG_DIR is used.
         */
        val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext

        if (loggerContext != null) {
            try {
                loggerContext.reset()

                val configurator = JoranConfigurator()
                configurator.context = loggerContext

                val configUrl =
                    CanopyLogging::class.java.getResource("/canopy-logback.xml")
                        ?: throw IllegalStateException("canopy-logback.xml not found on classpath")

                configurator.doConfigure(configUrl)
            } catch (e: Exception) {
                // Do not rely on logging here
                System.err.println("Failed to reload Logback configuration")
                e.printStackTrace()
            }
        } else {
            System.err.println("[CanopyLogging] LoggerContext is not Logback")
        }

        /*
         * Cosmetic startup banner
         */
        ConsoleBanner.print(config.engineVersion, config.bannerMode)

        /*
         * Global MDC context
         */
        LogContext.setGlobal(
            "runId" to config.runId,
            "engineVersion" to config.engineVersion
        )

        /*
         * Track session start
         */
        val now = Instant.now()
        startedAt.set(now)

        sessionLogger.info(
            "event" to "session.start",
            "schema" to "canopy-log-v1",
            "startedAt" to now.toString(),
            "runId" to config.runId,
            "engineVersion" to config.engineVersion,
            "runDir" to runDir.toString()
        ) { "Session start" }

        bootstrapLogger.info(
            "event" to "logging.init",
            "runDir" to runDir.toString()
        ) { "Canopy logging initialized" }
    }

    /**
     * Ends the logging session.
     */
    fun end(reason: String = "normal", t: Throwable? = null) {
        val start = startedAt.get()
        val now = Instant.now()

        val durationMs =
            start?.let { Duration.between(it, now).toMillis() }

        if (t != null) {
            sessionLogger.error(
                t = t,
                "event" to "session.end",
                "reason" to reason,
                "endedAt" to now.toString(),
                "durationMs" to durationMs
            ) { "Session end" }
        } else {
            sessionLogger.info(
                "event" to "session.end",
                "reason" to reason,
                "endedAt" to now.toString(),
                "durationMs" to durationMs
            ) { "Session end" }
        }
    }
}
