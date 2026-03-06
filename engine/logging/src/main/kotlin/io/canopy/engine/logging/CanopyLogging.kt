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
import io.canopy.engine.logging.CanopyLogging.end
import io.canopy.engine.logging.CanopyLogging.init
import io.canopy.engine.logging.util.ConsoleBanner
import org.slf4j.LoggerFactory

/**
 * Programmatic logging bootstrap for the Canopy engine.
 *
 * Why this exists:
 * - Initializes per-run log directories
 * - Sets up system properties (LOG_DIR) for logback.xml
 * - Reloads Logback configuration dynamically
 * - Provides startup banner and session tracking
 *
 * Configuration:
 * - Log routing, levels, and appenders are defined in logback.xml
 * - This class handles directory creation and runtime configuration
 *
 * Output layout (per run):
 * - Console: all logs except DEBUG/INFO from engine (WARN/ERROR always visible)
 * - canopy.log: all logs in plain text (no colors)
 * - canopy.json: structured JSON logs (machine readable)
 *
 * Important:
 * - [init] must be called once, as early as possible, before any logging occurs
 * - If the active SLF4J backend is not Logback, this becomes a no-op
 */
object CanopyLogging {

    /**
     * Logging configuration for a single engine run/session.
     *
     * Note: Log levels and rolling policies are now configured in logback.xml.
     * This Config only controls directory structure and startup behavior.
     */
    data class Config(
        val baseLogDir: Path = Path.of(".canopy").resolve("logs"),
        val runId: String = defaultRunFolderName(), // canopy-YYYY-MM-dd-HH-mm
        val engineVersion: String = "unknown",
        val bannerMode: ConsoleBanner.Mode = ConsoleBanner.Mode.GRADIENT,
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
     * What it does:
     * - Creates the run directory under baseLogDir
     * - Sets LOG_DIR system property (used by logback.xml)
     * - Reloads Logback configuration from logback.xml
     * - Prints startup banner
     * - Sets global MDC context (runId, engineVersion)
     * - Records session start time
     */
    fun init(config: Config) {
        // Create per-run directory: <baseLogDir>/<runId>/
        val runDir = config.baseLogDir.resolve(config.runId)
        if (!runDir.exists()) runDir.createDirectories()

        // Set system property BEFORE any logging occurs (so logback.xml can use it)
        System.setProperty("LOG_DIR", runDir.toAbsolutePath().toString())
        System.err.println("[CanopyLogging] LOG_DIR set to: ${System.getProperty("LOG_DIR")}")

        // Reload Logback configuration to use the updated LOG_DIR
        val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext
        if (loggerContext != null) {
            try {
                loggerContext.reset()
                val configurator = JoranConfigurator()
                configurator.context = loggerContext
                configurator.doConfigure(CanopyLogging::class.java.getResourceAsStream("/logback.xml"))
                System.err.println("[CanopyLogging] Logback configuration reloaded successfully")
            } catch (e: Exception) {
                System.err.println("Failed to reload Logback configuration: ${e.message}")
                e.printStackTrace()
            }
        } else {
            System.err.println("[CanopyLogging] LoggerContext is not Logback")
        }

        // Print the startup banner early. This is cosmetic, but helps users confirm startup.
        ConsoleBanner.print(config.engineVersion, config.bannerMode)

        // Global context goes into MDC so it is available on all log lines.
        // Scoped context (frame/nodePath, etc.) should be set by callers as needed.
        LogContext.setGlobal(
            "runId" to config.runId,
            "engineVersion" to config.engineVersion
        )

        // Record start time for session duration calculation
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
}
