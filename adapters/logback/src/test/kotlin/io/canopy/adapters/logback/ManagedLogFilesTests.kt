package io.canopy.adapters.logback

import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import io.canopy.engine.logging.engineLogger
import io.canopy.engine.logging.logger
import org.junit.jupiter.api.io.TempDir

class ManagedLogFilesTests {
    @TempDir
    lateinit var directory: Path

    private fun config(
        id: String,
        diagnostic: Boolean = false,
        retention: LogbackLogging.Retention = LogbackLogging.Retention(),
    ) = LogbackLogging.Config(
        baseLogDir = directory,
        runId = id,
        banner = false,
        mode = if (diagnostic) LogbackLogging.Mode.DIAGNOSTIC else LogbackLogging.Mode.STANDARD,
        retention = retention
    )

    @Test
    fun `standard writes two current text files and archives previous logs at the next start`() {
        // Arrange
        val first = LogbackLogging(config("first")).start("test")
        try {
            engineLogger("probe").debug { "first-engine" }
            logger("example.game").debug { "first-game" }
        } finally {
            first.close()
        }
        assertTrue("first-engine" in directory.resolve("engine.log").readText())
        assertTrue("first-game" in directory.resolve("app.log").readText())

        // Act
        val second = LogbackLogging(config("second")).start("test")
        try {
            logger("example.game").info { "second-game" }
        } finally {
            second.close()
        }

        // Assert
        assertTrue("first-engine" in directory.resolve("history/first/engine.log").readText())
        assertTrue("first-game" in directory.resolve("history/first/app.log").readText())
        assertFalse("first-game" in directory.resolve("app.log").readText())
        assertTrue("second-game" in directory.resolve("app.log").readText())
        assertTrue(Files.walk(directory).use { paths -> paths.noneMatch { it.toString().endsWith(".jsonl") } })
        val visible = Files.list(directory).use { paths ->
            paths.map { it.fileName.toString() }.filter { !it.startsWith('.') }.toList()
        }
        assertEquals(setOf("engine.log", "app.log", "history"), visible.toSet())
    }

    @Test
    fun `overlapping standard sessions use separate text only files and preserve current output`() {
        // Arrange
        val first = ManagedLogFiles.open(config("first"), "first")
        val second = ManagedLogFiles.open(config("second"), "second")
        try {
            // Act
            first.directory.resolve("engine.log").writeText("active-first")
            second.directory.resolve("engine.log").writeText("active-second")
            second.cleanupHistory()

            // Assert
            assertEquals(directory.toRealPath(), first.directory)
            assertEquals(directory.resolve("history/second"), second.directory)
            assertEquals("active-first", directory.resolve("engine.log").readText())
            assertEquals("active-second", second.directory.resolve("engine.log").readText())
            assertFalse(second.json)
        } finally {
            second.close()
            first.close()
        }
    }

    @Test
    fun `an external process holding current lease causes a safe text only fallback`() {
        // Arrange
        withExternalLease(directory.resolve(".canopy-current.lock")) {
            // Act
            val run = ManagedLogFiles.open(config("concurrent"), "concurrent")
            try {
                // Assert
                assertEquals(directory.resolve("history/concurrent"), run.directory)
                assertFalse(run.json)
                assertFalse(Files.exists(directory.resolve("engine.log")))
            } finally {
                run.close()
            }
        }
    }

    @Test
    fun `unknown current files and markers are preserved and never adopted`() {
        // Arrange
        directory.resolve("engine.log").writeText("user-engine")
        directory.resolve("app.log").writeText("user-game")
        directory.resolve(".canopy-current.properties").writeText("unrecognized user marker")

        // Act
        val run = ManagedLogFiles.open(config("safe-fallback"), "safe-fallback")
        run.close()

        // Assert
        assertEquals(directory.resolve("history/safe-fallback"), run.directory)
        assertEquals("user-engine", directory.resolve("engine.log").readText())
        assertEquals("user-game", directory.resolve("app.log").readText())
        assertEquals("unrecognized user marker", directory.resolve(".canopy-current.properties").readText())
    }

    @Test
    fun `a crash during archive recovers already moved logs without replacing them`() {
        // Arrange
        val abandoned = ManagedLogFiles.open(config("abandoned"), "abandoned")
        directory.resolve("engine.log").writeText("crashed-engine")
        directory.resolve("app.log").writeText("crashed-game")
        abandoned.close() // Releases the OS lease, leaving the same current ownership metadata as an abrupt exit.
        Files.move(directory.resolve("engine.log"), directory.resolve("history/abandoned/engine.log"))

        // Act
        val recovered = ManagedLogFiles.open(config("recovered"), "recovered")
        recovered.close()

        // Assert
        assertEquals(directory.toRealPath(), recovered.directory)
        assertEquals("crashed-engine", directory.resolve("history/abandoned/engine.log").readText())
        assertEquals("crashed-game", directory.resolve("history/abandoned/app.log").readText())
        assertEquals("", directory.resolve("engine.log").readText())
    }

    @Test
    fun `symlinked history and current files are never followed or overwritten`() {
        // Arrange
        val outside = Files.createDirectory(directory.resolve("outside"))
        val base = Files.createDirectory(directory.resolve("project"))
        outside.resolve("engine.log").writeText("outside-owned")
        Files.createSymbolicLink(base.resolve("engine.log"), outside.resolve("engine.log"))
        Files.createSymbolicLink(base.resolve("history"), outside)

        // Act
        val run = ManagedLogFiles.open(config("safe").copy(baseLogDir = base), "safe")
        run.close()

        // Assert
        assertEquals(base.resolve("safe"), run.directory)
        assertEquals("outside-owned", outside.resolve("engine.log").readText())
        assertTrue(Files.isSymbolicLink(base.resolve("history")))
        assertTrue(Files.isSymbolicLink(base.resolve("engine.log")))
    }

    @Test
    fun `history refuses substituted symlink ancestor before moving recognized current logs`() {
        // Arrange
        val base = Files.createDirectory(directory.resolve("project"))
        val previous = ManagedLogFiles.open(config("previous").copy(baseLogDir = base), "previous")
        base.resolve("engine.log").writeText("previous-engine")
        previous.close()
        val outside = directory.resolve("outside")
        Files.move(base.resolve("history"), outside)
        Files.createSymbolicLink(base.resolve("history"), outside)

        // Act
        val run = ManagedLogFiles.open(config("next").copy(baseLogDir = base), "next")
        run.close()

        // Assert
        assertEquals(base.resolve("next"), run.directory)
        assertEquals("previous-engine", base.resolve("engine.log").readText())
        assertFalse(Files.exists(outside.resolve("previous/engine.log")))
    }

    @Test
    fun `retention protects newest ten active current unknown and malformed data`() {
        // Arrange
        repeat(12) { completed("old-$it", it) }
        val active = ManagedLogFiles.open(config("active", diagnostic = true), "active")
        val current = ManagedLogFiles.open(config("current", retention = LogbackLogging.Retention(10, 1)), "current")
        val unknown = Files.createDirectory(directory.resolve("legacy"))
        unknown.resolve("user.txt").writeText("preserved")
        val malformed = Files.createDirectory(directory.resolve("malformed"))
        malformed.resolve(".canopy-run.properties").writeText(
            "schema=canopy-managed-logs-v1\nrunId=malformed\nstate=complete\nvalue=\\uQQQQ"
        )
        directory.resolve("old-0").resolve("engine.log").writeText("oldest-data")
        try {
            // Act
            val result = current.cleanupHistory()

            // Assert
            assertEquals(10, result.runs)
            assertTrue(result.bytes > 1) // Preserve ten even though they exceed the byte target.
            assertFalse(Files.exists(directory.resolve("old-0")))
            assertFalse(Files.exists(directory.resolve("old-1")))
            (2..11).forEach { assertTrue(Files.exists(directory.resolve("old-$it/engine.log"))) }
            assertTrue(Files.exists(active.directory))
            assertTrue(Files.exists(directory.resolve("engine.log")))
            assertEquals("preserved", unknown.resolve("user.txt").readText())
            assertTrue(Files.exists(malformed.resolve(".canopy-run.properties")))
        } finally {
            active.close()
            current.close()
        }
    }

    @Test
    fun `completed histories below budget keep more than ten runs`() {
        // Arrange
        repeat(12) { completed("small-$it", it, payload = "small") }
        val current = ManagedLogFiles.open(config("current"), "current")
        try {
            // Act and Assert
            assertEquals(12, current.cleanupHistory().runs)
            repeat(12) { assertTrue(Files.exists(directory.resolve("small-$it"))) }
        } finally {
            current.close()
        }
    }

    @Test
    fun `external active history lease and unknown files prevent pruning`() {
        // Arrange
        completed("externally-active", 0)
        completed("user-extended", 1)
        directory.resolve("user-extended/user.txt").writeText("user-owned")
        val current = ManagedLogFiles.open(config("current", retention = LogbackLogging.Retention(0, 0)), "current")
        try {
            withExternalLease(directory.resolve("externally-active/.canopy-run.lock")) {
                // Act
                current.cleanupHistory()

                // Assert
                assertTrue(Files.exists(directory.resolve("externally-active/engine.log")))
                assertEquals("user-owned", directory.resolve("user-extended/user.txt").readText())
            }
        } finally {
            current.close()
        }
    }

    @Test
    fun `external cleanup lease defers pruning until another process releases it`() {
        // Arrange
        completed("old", 0)
        val current = ManagedLogFiles.open(config("current", retention = LogbackLogging.Retention(0, 0)), "current")
        try {
            // Act and Assert
            withExternalLease(directory.resolve(".canopy-history.lock")) {
                current.cleanupHistory()
                assertTrue(Files.exists(directory.resolve("old/engine.log")))
            }
            current.cleanupHistory()
            assertFalse(Files.exists(directory.resolve("old")))
        } finally {
            current.close()
        }
    }

    @Test
    fun `reserved run names traversal and reused runs fail without disturbing existing files`() {
        // Arrange
        val prior = ManagedLogFiles.open(config("retained", diagnostic = true), "retained")
        prior.directory.resolve("engine.log").writeText("retained-data")
        prior.close()

        // Act and Assert
        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            ManagedLogFiles.open(config("retained"), "retained")
        }
        for (name in listOf("history", "engine.log", "app.log", ".canopy-current.lock", "../escape", "C:escape")) {
            assertFailsWith<IllegalArgumentException> { ManagedLogFiles.open(config(name), name) }
        }
        assertEquals("retained-data", directory.resolve("retained/engine.log").readText())
    }

    private fun completed(id: String, order: Int, payload: String = "x".repeat(1024)) {
        val run = ManagedLogFiles.open(config(id, diagnostic = true), id)
        run.directory.resolve("engine.log").writeText(payload)
        run.close()
        val marker = run.directory.resolve(".canopy-run.properties")
        val properties = Properties().apply { Files.newInputStream(marker).use(::load) }
        properties.setProperty(
            "startedAt",
            Instant.parse("2026-01-01T00:00:00Z").plusSeconds(order.toLong()).toString()
        )
        Files.newOutputStream(marker).use { properties.store(it, "Test history ordering") }
    }

    /** A separate JVM holds a real OS lock; stdout is an IPC handshake, not an engine diagnostic. */
    private fun withExternalLease(path: Path, block: () -> Unit) {
        val source = directory.resolve("LeaseProbe.java")
        source.writeText(
            """
            import java.nio.channels.FileChannel;
            import java.nio.file.Path;
            import java.nio.file.StandardOpenOption;
            class LeaseProbe {
                public static void main(String[] args) throws Exception {
                    try (var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                         var lease = channel.lock()) {
                        System.out.println("READY");
                        System.out.flush();
                        System.in.read();
                    }
                }
            }
            """.trimIndent()
        )
        val executable = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
        val java = Path.of(System.getProperty("java.home"), "bin", executable)
        val process = ProcessBuilder(java.toString(), "--source", "25", source.toString(), path.toString())
            .redirectError(directory.resolve("lease-process.log").toFile()).start()
        try {
            val ready = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readLine() }
            assertEquals("READY", ready.get(30, TimeUnit.SECONDS))
            block()
        } finally {
            runCatching {
                process.outputStream.write('\n'.code)
                process.outputStream.flush()
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
        }
    }
}
