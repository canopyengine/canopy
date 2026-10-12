package io.canopy.adapters.logback

import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.Appender
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.read.ListAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.spi.FilterReply
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.logging.LogContext
import io.canopy.engine.logging.LoggingPolicy
import io.canopy.engine.logging.LoggingSession
import io.canopy.engine.logging.engineLogger
import io.canopy.engine.logging.logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.Marker

class LogbackLoggingTests {
    @TempDir
    lateinit var directory: Path
    private val context = LoggerFactory.getILoggerFactory() as LoggerContext
    private val root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    private val engine = context.getLogger("io.canopy.engine")
    private val host = ListAppender<ILoggingEvent>()
    private val sessions = mutableListOf<LoggingSession>()
    private var rootLevel: Level? = null
    private var engineLevel: Level? = null
    private var engineAdditive = true
    private var previousDirectory: String? = null
    private var previousMdc: Map<String, String>? = null

    @BeforeEach
    fun setup() {
        rootLevel = root.level
        engineLevel = engine.level
        engineAdditive = engine.isAdditive
        previousDirectory = System.getProperty("LOG_DIR")
        previousMdc = MDC.getCopyOfContextMap()
        root.level = Level.INFO
        engine.level = null
        engine.isAdditive = true
        host.context = context
        host.start()
        root.addAppender(host)
        System.setProperty("LOG_DIR", "host-owned-directory")
        LogContext.setGlobal("host" to "preserved", "runId" to "host-run")
        MDC.put("caller", "preserved")
    }

    @AfterEach
    fun cleanup() {
        sessions.forEach { it.close() }
        root.detachAppender(host)
        host.stop()
        root.level = rootLevel
        engine.level = engineLevel
        engine.isAdditive = engineAdditive
        if (previousDirectory ==
            null
        ) {
            System.clearProperty("LOG_DIR")
        } else {
            System.setProperty("LOG_DIR", previousDirectory)
        }
        LogContext.clearGlobal()
        if (previousMdc == null) MDC.clear() else MDC.setContextMap(previousMdc)
    }

    private fun open(id: String, preserveHostOutput: Boolean = false): LoggingSession = LogbackLogging(
        LogbackLogging.Config(
            directory,
            id,
            banner = false,
            preserveHostOutput = preserveHostOutput,
            mode = LogbackLogging.Mode.DIAGNOSTIC
        )
    ).start("test-version")
        .also { sessions += it }

    private fun appenders(): List<Appender<ILoggingEvent>> =
        root.iteratorForAppenders().asSequence().toList() + engine.iteratorForAppenders().asSequence().toList()

    @Test
    fun `default logging captures ordinary game and background events in files without console output`() {
        // Arrange: model Logback's default console setup, with no project logback.xml.
        val output = ByteArrayOutputStream()
        val previousOutput = System.out
        val console = ConsoleAppender<ILoggingEvent>()
        val encoder = PatternLayoutEncoder().also {
            it.context = context
            it.pattern = "%msg%n"
            it.start()
        }
        System.setOut(PrintStream(output))
        console.context = context
        console.encoder = encoder
        console.start()
        root.addAppender(console)
        val game = context.getLogger("example.default.game")
        val previousLevel = game.level
        val previousAdditive = game.isAdditive
        game.level = Level.ERROR
        game.isAdditive = false
        game.addAppender(console)
        try {
            val before = appenders()
            val session = open("default")

            // Act
            engineLogger("probe").debug { "engine-debug" }
            engineLogger("probe").error { "engine-error" }
            logger(game.name).debug { "game-debug" }
            logger(game.name).info { "game-info" }
            logger(game.name).warn { "game-warning" }
            Thread { logger(game.name).error { "game-background-error" } }.apply {
                start()
                join()
            }
            session.end("normal", null)
            session.close()

            // Assert
            assertEquals("", output.toString())
            assertTrue(host.list.isEmpty())
            val engineFile = directory.resolve("default/engine.log").readText()
            val gameFile = directory.resolve("default/app.log").readText()
            assertTrue("engine-debug" in engineFile)
            assertTrue("engine-error" in engineFile)
            assertTrue("session.start" in engineFile && "session.end" in engineFile)
            assertFalse("game-info" in engineFile)
            assertTrue("game-info" in gameFile)
            assertTrue("game-warning" in gameFile)
            assertTrue("game-background-error" in gameFile)
            assertTrue("game-debug" in gameFile)
            assertFalse("engine-error" in gameFile)
            val records = directory.resolve("default/app.jsonl").readText().lineSequence().filter { it.isNotBlank() }
                .map { Json.parseToJsonElement(it).jsonObject }.toList()
            assertEquals(4, records.size)
            assertTrue(records.all { it.getValue("runId").jsonPrimitive.content == "default" })
            assertTrue(records.all { it.getValue("engineVersion").jsonPrimitive.content == "test-version" })
            assertEquals(before, appenders())
            assertEquals(Level.ERROR, game.level)
            assertFalse(game.isAdditive)
            assertTrue(console.isStarted)
            logger(game.name).error { "host-console-restored" }
            assertTrue("host-console-restored" in output.toString())
        } finally {
            System.setOut(previousOutput)
            root.detachAppender(console)
            game.detachAppender(console)
            game.level = previousLevel
            game.isAdditive = previousAdditive
            console.stop()
            encoder.stop()
        }
    }

    @Test
    fun `explicit host and preserving policies leave host output untouched`() {
        // Arrange
        val before = appenders()
        val hostSession = LoggingPolicy.Host.start("test-version")
        val managed = open("preserving", preserveHostOutput = true)

        // Act
        hostSession.withContext { logger("example.host").info { "host-message" } }
        managed.withContext { logger("example.game").info { "preserved-game-message" } }
        logger("example.game").info { "unscoped-host-message" }
        managed.close()
        hostSession.close()

        // Assert
        assertEquals(before, appenders())
        assertTrue(host.list.any { it.formattedMessage == "host-message" })
        assertTrue(host.list.any { it.formattedMessage == "preserved-game-message" })
        assertTrue(host.list.any { it.formattedMessage == "unscoped-host-message" })
        val appFile = directory.resolve("preserving/app.log").readText()
        assertTrue("preserved-game-message" in appFile)
        assertFalse("unscoped-host-message" in appFile)
        assertFalse("host-message" in appFile)
    }

    @Test
    fun `last active session receives unscoped events after either close order`() {
        for (closeFirst in listOf(true, false)) {
            // Arrange
            val first = open("overlap-first-$closeFirst")
            val second = open("overlap-second-$closeFirst")
            val closed = if (closeFirst) first else second
            val remaining = if (closeFirst) second else first
            val remainingId = if (closeFirst) "overlap-second-$closeFirst" else "overlap-first-$closeFirst"
            val closedId = if (closeFirst) "overlap-first-$closeFirst" else "overlap-second-$closeFirst"

            // Act
            logger("example.game").info { "ambiguous-game-message" }
            closed.close()
            logger("example.game").info { "remaining-unscoped-message" }
            remaining.withContext { engineLogger("probe").debug { "remaining-engine-debug" } }

            // Assert
            assertTrue(host.list.isEmpty())
            remaining.close()
            assertTrue("remaining-unscoped-message" in directory.resolve("$remainingId/app.log").readText())
            assertFalse("remaining-unscoped-message" in directory.resolve("$closedId/app.log").readText())
            assertFalse("ambiguous-game-message" in directory.resolve("$remainingId/app.log").readText())
            assertTrue("remaining-engine-debug" in directory.resolve("$remainingId/engine.log").readText())
            assertTrue(root.isAttached(host))
        }
    }

    @Test
    fun `incompatible overlapping output policies fail without disturbing active session`() {
        // Arrange
        val existing = open("managed")
        val before = appenders()

        // Act
        assertFailsWith<IllegalStateException> { open("incompatible", preserveHostOutput = true) }
        logger("example.game").info { "still-managed" }
        existing.close()

        // Assert
        assertEquals(4, before.size)
        assertTrue("still-managed" in directory.resolve("managed/app.log").readText())
        assertTrue(root.isAttached(host))
    }

    @Test
    fun `sessions isolate files and close only owned appenders without changing host configuration`() {
        // Arrange
        val before = appenders()
        val first = open("first")
        val second = open("second")
        val expected = IllegalStateException("scope")

        // Act
        first.withContext {
            engineLogger("probe").info { "first-engine" }
            logger("example.game").info { "first-app" }
        }
        assertSame(
            expected,
            assertFailsWith<IllegalStateException> {
                second.withContext { throw expected }
            }
        )
        first.end("normal", null)
        first.end("normal", null)
        first.close()
        first.close()
        second.withContext { engineLogger("probe").info { "second-engine" } }
        second.end("crash", expected)
        second.close()
        logger("example.host").info { "host-after" }

        // Assert
        val firstEngine = directory.resolve("first/engine.log").readText()
        val secondEngine = directory.resolve("second/engine.log").readText()
        assertTrue("first-engine" in firstEngine)
        assertFalse("second-engine" in firstEngine)
        assertTrue("second-engine" in secondEngine)
        assertFalse("first-engine" in secondEngine)
        assertTrue("first-app" in directory.resolve("first/app.log").readText())
        assertFalse("first-engine" in directory.resolve("first/app.log").readText())
        val json = directory.resolve("first/engine.jsonl").readText()
        assertTrue("\"runId\":\"first\"" in json)
        assertTrue("\"engineVersion\":\"test-version\"" in json)
        assertTrue("\"host\":\"preserved\"" in json)
        val records = json.lineSequence().filter {
            it.isNotBlank()
        }.map { Json.parseToJsonElement(it).jsonObject }.toList()
        assertEquals(1, records.count { "session.start" in it.getValue("message").jsonPrimitive.content })
        val ended = records.single { "session.end" in it.getValue("message").jsonPrimitive.content }
        assertTrue("reason=normal" in ended.getValue("message").jsonPrimitive.content)
        assertTrue("durationMs=" in ended.getValue("message").jsonPrimitive.content)
        val secondRecords = directory.resolve("second/engine.jsonl").readText().lineSequence()
            .filter { it.isNotBlank() }.map { Json.parseToJsonElement(it).jsonObject }.toList()
        val crashed = secondRecords.single { "session.end" in it.getValue("message").jsonPrimitive.content }
        assertTrue("reason=crash" in crashed.getValue("message").jsonPrimitive.content)
        assertTrue("scope" in crashed.getValue("stack_trace").jsonPrimitive.content)
        val appRecord = directory.resolve("first/app.jsonl").readText().lineSequence().filter { it.isNotBlank() }
            .map { Json.parseToJsonElement(it).jsonObject }.single()
        assertEquals("first-app", appRecord.getValue("message").jsonPrimitive.content)
        assertEquals("first", appRecord.getValue("runId").jsonPrimitive.content)
        assertEquals(before, appenders())
        assertTrue(context.isStarted)
        assertTrue(host.isStarted)
        assertEquals(Level.INFO, root.level)
        assertEquals(null, engine.level)
        assertTrue(engine.isAdditive)
        assertEquals("host-owned-directory", System.getProperty("LOG_DIR"))
        assertEquals("preserved", MDC.get("caller"))
        assertEquals(null, MDC.get("canopy.logging.session"))
        assertEquals("host-run", host.list.last().mdcPropertyMap["runId"])
    }

    @Test
    fun `managed capture honors host levels and non additive descendant routing`() {
        // Arrange
        val blocked = context.getLogger("io.canopy.engine.blocked")
        val oldLevel = blocked.level
        val oldAdditive = blocked.isAdditive
        val session = open("filtered", preserveHostOutput = true)
        try {
            blocked.level = Level.WARN
            blocked.isAdditive = false

            // Act
            session.withContext {
                engineLogger("blocked").info { "disabled-by-level" }
                engineLogger("blocked").warn { "blocked-by-routing" }
                engineLogger("probe").info { "delivered" }
            }
            session.close()

            // Assert
            val file = directory.resolve("filtered/engine.log").readText()
            assertFalse("disabled-by-level" in file)
            assertFalse("blocked-by-routing" in file)
            assertTrue("delivered" in file)
            assertEquals(Level.WARN, blocked.level)
            assertFalse(blocked.isAdditive)
        } finally {
            blocked.level = oldLevel
            blocked.isAdditive = oldAdditive
        }
    }

    @Test
    fun `failed first session restores host configuration and permits a later session`() {
        // Arrange
        val before = appenders()
        val failure = IllegalStateException("reject first session event")
        val filter = object : TurboFilter() {
            override fun decide(
                marker: Marker?,
                logger: Logger,
                level: Level,
                format: String?,
                params: Array<out Any>?,
                t: Throwable?,
            ): FilterReply {
                if (logger.name == "io.canopy.engine.session") throw failure
                return FilterReply.NEUTRAL
            }
        }
        filter.start()
        context.addTurboFilter(filter)
        try {
            // Act and Assert
            assertSame(failure, assertFailsWith<IllegalStateException> { open("first-failed") })
            assertEquals(before, appenders())
            assertEquals(Level.INFO, root.level)
            assertEquals(null, engine.level)
            assertTrue(host.isStarted)
        } finally {
            context.turboFilterList.remove(filter)
            filter.stop()
        }
        val subsequent = open("subsequent")
        logger("example.game").info { "subsequent-unscoped" }
        subsequent.close()
        assertTrue("subsequent-unscoped" in directory.resolve("subsequent/app.log").readText())
        assertEquals(before, appenders())
    }

    @Test
    fun `failed startup stops and detaches partially initialized session while another stays active`() {
        // Arrange
        val existing = open("existing")
        val before = appenders()
        val failure = IllegalStateException("host rejects session event")
        var partiallyStarted: List<RollingFileAppender<ILoggingEvent>> = emptyList()
        val filter = object : TurboFilter() {
            override fun decide(
                marker: Marker?,
                logger: Logger,
                level: Level,
                format: String?,
                params: Array<out Any>?,
                t: Throwable?,
            ): FilterReply {
                if (logger.name == "io.canopy.engine.session") {
                    partiallyStarted = appenders().filter { it !in before }.map {
                        @Suppress("UNCHECKED_CAST")
                        it as RollingFileAppender<ILoggingEvent>
                    }
                    throw failure
                }
                return FilterReply.NEUTRAL
            }
        }
        filter.start()
        context.addTurboFilter(filter)
        try {
            // Act
            assertSame(failure, assertFailsWith<IllegalStateException> { open("failed") })
        } finally {
            context.turboFilterList.remove(filter)
            filter.stop()
        }

        // Assert
        assertEquals(4, partiallyStarted.size)
        assertTrue(partiallyStarted.all { !it.isStarted && !it.encoder.isStarted && !it.rollingPolicy.isStarted })
        assertEquals(before, appenders())
        existing.withContext { engineLogger("probe").info { "existing-still-active" } }
        existing.close()
        assertTrue("existing-still-active" in directory.resolve("existing/engine.log").readText())
    }

    @Test
    fun `configured run names stay beneath base directory and existing output is preserved`() {
        // Arrange
        val session = open("retained")
        session.withContext { engineLogger("probe").info { "retained-record" } }
        session.close()
        val retained = directory.resolve("retained/engine.log").readText()
        val before = appenders()

        // Act and Assert
        assertFailsWith<java.nio.file.FileAlreadyExistsException> { open("retained") }
        for (name in listOf("", ".", "..", "../escape", "parent/child", "parent\\child", "C:escape")) {
            assertFailsWith<IllegalArgumentException> { open(name) }
        }
        assertEquals(retained, directory.resolve("retained/engine.log").readText())
        assertEquals(before, appenders())
        val policy =
            LogbackLogging(LogbackLogging.Config(directory, banner = false, mode = LogbackLogging.Mode.DIAGNOSTIC))
        sessions += policy.start("test-version")
        sessions += policy.start("test-version")
        assertEquals(3, Files.list(directory).use { paths -> paths.filter { Files.isDirectory(it) }.count() })
    }

    @Test
    fun `application exit callback remains captured until managed resources close`() {
        // Arrange
        val before = appenders()
        val app = object : App<AppConfig>() {
            override fun defaultConfig() = AppConfig()
            override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
        }.apply {
            logging(
                LogbackLogging(
                    LogbackLogging.Config(directory, "app", banner = false, mode = LogbackLogging.Mode.DIAGNOSTIC)
                )
            )
            onExit { logger("example.game").info { "application-final-message" } }
        }

        // Act
        app.enter()
        app.exit()
        app.exit()

        // Assert
        assertTrue("application-final-message" in directory.resolve("app/app.log").readText())
        assertTrue("session.end" in directory.resolve("app/engine.log").readText())
        assertEquals(before, appenders())
    }
}
