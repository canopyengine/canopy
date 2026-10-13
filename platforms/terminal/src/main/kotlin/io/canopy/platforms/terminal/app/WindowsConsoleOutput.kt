package io.canopy.platforms.terminal.app

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT

/** Enables ANSI interpretation before any screen output, restoring the caller's console modes on exit. */
internal fun enableWindowsConsoleOutput(): AutoCloseable {
    if (!System.getProperty("os.name").startsWith("Windows")) return AutoCloseable { }
    val restore = mutableListOf<() -> Unit>()
    try {
        for (stream in listOf(-11, -12)) {
            val handle = WindowsConsoleModes.handle(stream)
            val original = WindowsConsoleModes.read(handle) ?: continue // Redirected streams are not consoles.
            // Windows requires processed output alongside virtual terminal processing (SetConsoleMode).
            WindowsConsoleModes.write(handle, original or 0x0001 or 0x0004)
            restore += { WindowsConsoleModes.write(handle, original) }
        }
    } catch (error: Throwable) {
        restore.asReversed().forEach { runCatching(it) }
        throw error
    }
    return AutoCloseable { restore.asReversed().forEach { it() } }
}

/** Loaded only on Windows; uses the same JDK native access already required by Mordant's FFM backend. */
internal object WindowsConsoleModes {
    private val symbols = SymbolLookup.libraryLookup("kernel32", Arena.global())
    private val linker = Linker.nativeLinker()
    private val getHandle = linker.downcallHandle(
        symbols.find("GetStdHandle").orElseThrow(),
        FunctionDescriptor.of(ADDRESS, JAVA_INT)
    )
    private val getMode = linker.downcallHandle(
        symbols.find("GetConsoleMode").orElseThrow(),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)
    )
    private val setMode = linker.downcallHandle(
        symbols.find("SetConsoleMode").orElseThrow(),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)
    )

    fun handle(stream: Int): MemorySegment = getHandle.invokeWithArguments(stream) as MemorySegment

    fun read(handle: MemorySegment): Int? = Arena.ofConfined().use { arena ->
        val mode = arena.allocate(JAVA_INT)
        if (getMode.invokeWithArguments(handle, mode) as Int == 0) null else mode.get(JAVA_INT, 0)
    }

    fun write(handle: MemorySegment, mode: Int) {
        check(setMode.invokeWithArguments(handle, mode) as Int != 0) {
            "Cannot enable Windows ANSI console output; use a Windows 10+ console or Windows Terminal"
        }
    }
}
