package io.canopy.platforms.terminal.app

import kotlin.test.*
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_SHORT
import java.nio.charset.StandardCharsets.UTF_16LE
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit
import com.github.ajalt.mordant.rendering.Size
import org.junit.jupiter.api.io.TempDir

class WindowsConsoleOutputTests {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `fresh Windows console interprets ANSI and restores both output modes`() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        // Arrange: Gradle's test worker has piped output, so create a real console for this regression.
        val marker = directory.resolve("console-result.txt")
        val java = Path.of(System.getProperty("java.home"), "bin", "java.exe")
        val classpath = checkNotNull(System.getProperty("canopy.test.runtimeClasspath"))
        val arguments = directory.resolve("java-arguments.txt")
        arguments.toFile().writeText(
            listOf(
                "--enable-native-access=ALL-UNNAMED",
                "-cp",
                classpath,
                WindowsConsoleOutputProbe::class.java.name,
                marker.toString()
            ).joinToString("\n") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }
        )
        fun quote(value: String) = "'${value.replace("'", "''")}'"
        val script = "\$ErrorActionPreference='Stop'; \$p=Start-Process -FilePath ${quote(java.toString())} " +
            "-ArgumentList ${quote("\"@$arguments\"")} -PassThru; " +
            "if (-not \$p.WaitForExit(45000)) { \$p.Kill(); exit 124 }; exit \$p.ExitCode"

        // Act
        val process = ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-EncodedCommand",
            Base64.getEncoder().encodeToString(script.toByteArray(UTF_16LE))
        ).redirectErrorStream(true).start()
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Console probe timed out")

            // Assert
            val diagnostics = if (marker.toFile().exists()) {
                marker.toFile().readText()
            } else {
                process.inputStream.bufferedReader().readText()
            }
            assertEquals(0, process.exitValue(), diagnostics)
            assertEquals("OK", marker.toFile().readText())
        } finally {
            process.destroyForcibly()
        }
    }
}

/** Native acceptance runs in its own Windows console, rather than Gradle's redirected test output. */
object WindowsConsoleOutputProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val marker = Path.of(args.single()).toFile()
        try {
            val stdout = WindowsConsoleModes.handle(-11)
            val stderr = WindowsConsoleModes.handle(-12)
            val originalOut = checkNotNull(WindowsConsoleModes.read(stdout))
            val originalErr = checkNotNull(WindowsConsoleModes.read(stderr))
            try {
                val disabledOut = originalOut and 0x0004.inv()
                val disabledErr = originalErr and 0x0004.inv()
                WindowsConsoleModes.write(stdout, disabledOut)
                WindowsConsoleModes.write(stderr, disabledErr)
                val surface = TerminalSurface(runtimeTerminal(), { Size(80, 24) }, { System.out.print(it) }, { false })
                surface.openSession()
                try {
                    check(WindowsConsoleModes.read(stdout) == disabledOut or 0x0005)
                    check(WindowsConsoleModes.read(stderr) == disabledErr or 0x0005)
                    // Cursor positioning is interpreted, not printed as literal escape bytes.
                    System.out.print("\u001b[1;1H\u001b[38;2;120;240;120mX\u001b[0m")
                    System.out.flush()
                    val symbols = SymbolLookup.libraryLookup("kernel32", Arena.global())
                    val info = Linker.nativeLinker().downcallHandle(
                        symbols.find("GetConsoleScreenBufferInfo").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)
                    )
                    Arena.ofConfined().use { arena ->
                        val buffer = arena.allocate(22, 2)
                        check(info.invokeWithArguments(stdout, buffer) as Int != 0)
                        check(buffer.get(JAVA_SHORT, 4).toInt() == 1 && buffer.get(JAVA_SHORT, 6).toInt() == 0)
                    }
                } finally {
                    surface.closeSession()
                }
                check(WindowsConsoleModes.read(stdout) == disabledOut)
                check(WindowsConsoleModes.read(stderr) == disabledErr)
            } finally {
                WindowsConsoleModes.write(stdout, originalOut)
                WindowsConsoleModes.write(stderr, originalErr)
            }
            marker.writeText("OK")
        } catch (error: Throwable) {
            marker.writeText(error.stackTraceToString())
            throw error
        }
    }
}
