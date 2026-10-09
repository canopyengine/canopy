package io.canopy.adapters.logback

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
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
 * File-only logging: current engine/session and game diagnostics use `.canopy/logs/engine.log` and `app.log`.
 * [Mode.DIAGNOSTIC] adds JSONL and a separate run directory. Concurrent standard runs use separate text-only folders.
 * Defaults temporarily replace host appenders with files, enabling engine and game DEBUG logging.
 * The last managed session restores the previous host configuration without resetting or stopping its backend.
 * Unscoped events belong to the sole active session; overlapping applications must use their logging context.
 * Custom Canopy providers are unchanged. Start/end/close run on the application lifecycle thread.
 * Select [LoggingPolicy.Host] to leave host output entirely under the caller's control.
 */
class LogbackLogging(private val config: Config = Config()) : LoggingPolicy {
    /** Diagnostic detail and layout selected before application entry; changing mode during a run is unsupported. */
    enum class Mode {
        /** Two current text files; previous runs are archived under `history` at the next start. */
        STANDARD,

        /** Separate run folders containing text and JSONL files for structured diagnosis. */
        DIAGNOSTIC,
    }

    /**
     * Completed-history caps, applied at startup and after isolated runs close.
     * Oldest owned runs are removed until both [maxRuns] and [targetBytes] fit; fewer runs may remain.
     * Current/active and unrecognized data are excluded. Cleanup is best-effort when files cannot be removed.
     */
    data class Retention(val maxRuns: Int = 10, val targetBytes: Long = 100L * 1024 * 1024) {
        init {
            require(maxRuns >= 0) { "maxRuns must not be negative" }
            require(targetBytes >= 0) { "targetBytes must not be negative" }
        }
    }

    /**
     * Managed output options; an explicit [runId] must identify a new run beneath [baseLogDir].
     * [preserveHostOutput] retains host appenders, levels and routing, capturing only scoped events in files.
     * Sessions with different host-output policies cannot overlap in the same Logback context.
     * [banner] controls intentional startup artwork, independently of diagnostic logging.
     */
    data class Config(
        val baseLogDir: Path = Path.of(".canopy", "logs"),
        val runId: String? = null,
        val banner: Boolean = true,
        val preserveHostOutput: Boolean = false,
        val mode: Mode = Mode.STANDARD,
        val retention: Retention = Retention(),
    ) {
        companion object {
            /**
             * Explicit installed-game storage: Windows LOCALAPPDATA, Linux XDG_STATE_HOME or macOS Library/Logs.
             * [publisher] and [game] must be portable single directory names. Environment fallbacks use user.home.
             * The default [Config] remains project-relative; [baseLogDir] accepts an explicit custom path.
             */
            fun forInstalledGame(publisher: String, game: String, mode: Mode = Mode.STANDARD): Config =
                Config(baseLogDir = installedLogDirectory(publisher, game), mode = mode)
        }
    }

    override fun start(engineVersion: String): LoggingSession {
        val context = LoggerFactory.getILoggerFactory() as? LoggerContext
            ?: error("Managed logging requires the SLF4J Logback backend; select LoggingPolicy.Host for other backends")
        val token = UUID.randomUUID().toString()
        val runId = config.runId ?: "${FOLDER_TIME.format(ZonedDateTime.now())}-$token"
        requireLogDirectoryName(runId)
        val files = ManagedLogFiles.open(config, runId)
        val directory = files.directory
        val session = Session(context, files, runId, token, engineVersion, config.preserveHostOutput)
        try {
            session.open()
            session.withContext {
                val history = files.cleanupHistory()
                if (history.bytes > config.retention.targetBytes || history.runs > config.retention.maxRuns) {
                    engineLogger("session").warn(
                        "event" to "history.budget",
                        "historyBytes" to history.bytes,
                        "targetBytes" to config.retention.targetBytes,
                        "historyRuns" to history.runs,
                        "maxRuns" to config.retention.maxRuns
                    ) { "Unable to trim all completed log history; active and unrecognized data were preserved" }
                }
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
        private val files: ManagedLogFiles,
        private val runId: String,
        private val token: String,
        private val engineVersion: String,
        private val preserveHostOutput: Boolean,
    ) : LoggingSession {
        val startedAt: Instant = Instant.now()
        private val owned = mutableListOf<OwnedAppender>()
        private var ended = false
        private var registered = false

        @Volatile
        private var closed = false

        fun open() {
            Routing.open(context, token, preserveHostOutput)
            registered = true
            for (engine in listOf(true, false)) {
                for (json in if (files.json) listOf(false, true) else listOf(false)) addAppender(engine, json)
            }
        }

        private fun addAppender(engine: Boolean, json: Boolean) {
            val category = if (engine) "engine" else "app"
            val extension = if (json) "jsonl" else "log"
            val logger = context.getLogger(if (engine) ENGINE_NAMESPACE else org.slf4j.Logger.ROOT_LOGGER_NAME)
            val appender = object : RollingFileAppender<ILoggingEvent>() {
                override fun append(eventObject: ILoggingEvent) {
                    // Supply session metadata even for ordinary unscoped game and library logging.
                    val event = object : ILoggingEvent by eventObject {
                        override fun getMDCPropertyMap(): Map<String, String> = eventObject.mdcPropertyMap + mapOf(
                            SESSION_KEY to token,
                            "runId" to runId,
                            "engineVersion" to engineVersion
                        )
                    }
                    super.append(event)
                }
            }
            appender.context = context
            appender.name = "canopy-$token-$category-$extension"
            appender.file = files.directory.resolve("$category.$extension").toString()
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
                it.fileNamePattern = files.rollingDirectory.resolve("$category.%d{yyyy-MM-dd}.%i.$extension").toString()
                it.setMaxFileSize(FileSize.valueOf("10MB"))
                // Completed-run retention owns cleanup; rolling must never remove part of an active run.
            }
            // Track all children before starting any so partial initialization can release them.
            owned += OwnedAppender(logger, appender, encoder, rolling)
            appender.addFilter(object : Filter<ILoggingEvent>() {
                override fun decide(event: ILoggingEvent): FilterReply {
                    val engineEvent = event.loggerName == ENGINE_NAMESPACE ||
                        event.loggerName.startsWith("$ENGINE_NAMESPACE.")
                    return if (Routing.destination(this@Session.context, event.mdcPropertyMap[SESSION_KEY]) == token &&
                        engineEvent == engine
                    ) {
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
            if (registered) {
                registered = false
                attempt { Routing.close(context, token) }
            }
            attempt { files.close() }
            failure?.let { throw it }
        }
    }

    private data class OwnedAppender(
        val logger: ch.qos.logback.classic.Logger,
        val appender: RollingFileAppender<ILoggingEvent>,
        val encoder: Encoder<ILoggingEvent>,
        val rolling: SizeAndTimeBasedRollingPolicy<ILoggingEvent>,
    )

    /** Reference-counted temporary configuration; host appenders are detached, never stopped or reset. */
    private object Routing {
        private data class HostLogger(
            val logger: Logger,
            val level: Level?,
            val additive: Boolean,
            val appenders: List<ch.qos.logback.core.Appender<ILoggingEvent>>,
        )

        private data class State(
            val preserveHostOutput: Boolean,
            val host: List<HostLogger>,
            val sessions: MutableSet<String> = mutableSetOf(),
        )

        private val contexts = mutableMapOf<LoggerContext, State>()

        @Synchronized
        fun open(context: LoggerContext, token: String, preserveHostOutput: Boolean) {
            val existing = contexts[context]
            check(existing == null || existing.preserveHostOutput == preserveHostOutput) {
                "Overlapping Logback sessions must use the same preserveHostOutput setting"
            }
            val state = existing ?: State(
                preserveHostOutput,
                if (preserveHostOutput) {
                    emptyList()
                } else {
                    // Materialize the namespace before taking the snapshot, including its inherited settings.
                    context.getLogger(ENGINE_NAMESPACE)
                    context.getLogger("org.slf4j")
                    context.getLogger("ch.qos.logback")
                    context.loggerList.map { logger ->
                        HostLogger(
                            logger,
                            logger.level,
                            logger.isAdditive,
                            logger.iteratorForAppenders().asSequence().toList()
                        )
                    }
                }
            ).also { created ->
                for (host in created.host) {
                    host.appenders.forEach(host.logger::detachAppender)
                    host.logger.level = when (host.logger.name) {
                        org.slf4j.Logger.ROOT_LOGGER_NAME -> Level.DEBUG
                        ENGINE_NAMESPACE -> Level.DEBUG
                        "org.slf4j", "ch.qos.logback" -> Level.WARN
                        else -> null
                    }
                    host.logger.isAdditive = true
                }
                contexts[context] = created
            }
            state.sessions += token
        }

        @Synchronized
        fun destination(context: LoggerContext, token: String?): String? {
            val state = contexts[context] ?: return null
            return if (token != null) {
                token.takeIf { it in state.sessions }
            } else if (!state.preserveHostOutput) {
                state.sessions.singleOrNull()
            } else {
                null
            }
        }

        @Synchronized
        fun close(context: LoggerContext, token: String) {
            val state = contexts[context] ?: return
            state.sessions -= token
            if (state.sessions.isNotEmpty()) return
            contexts.remove(context)
            for (host in state.host) {
                host.logger.level = host.level
                host.logger.isAdditive = host.additive
                host.appenders.forEach(host.logger::addAppender)
            }
        }
    }

    private companion object {
        const val ENGINE_NAMESPACE = "io.canopy.engine"
        const val SESSION_KEY = "canopy.logging.session"
        val FOLDER_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
