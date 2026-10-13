package io.canopy.platforms.terminal.app

import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.StandardTerminalInterface

class RuntimeTerminalTests {
    @Test
    fun `macOS delegates settings and input to safe backend while geometry stays native`() {
        // Arrange
        val calls = mutableListOf<String>()
        val safeInput = object : StandardTerminalInterface() {
            override fun enterRawMode(mouseTracking: MouseTracking): AutoCloseable {
                calls += "raw:$mouseTracking"
                return AutoCloseable { calls += "restore" }
            }
            override fun readLineOrNull(hideInput: Boolean): String {
                calls += "line:$hideInput"
                return "draft"
            }
            override fun readInputEvent(timeout: TimeMark, mouseTracking: MouseTracking): InputEvent? {
                calls += "event:$mouseTracking"
                return null
            }
            override fun getTerminalSize(): Size = error("Must not spawn stty for geometry")
        }
        val size = Size(80, 24)
        val geometry = object : StandardTerminalInterface() {
            override fun getTerminalSize() = size
            override fun shouldAutoUpdateSize() = true
            override fun enterRawMode(mouseTracking: MouseTracking): AutoCloseable = error("Unsafe FFM settings")
        }
        val terminal = MacosTerminalInterface(safeInput, geometry)

        // Act
        terminal.enterRawMode(MouseTracking.Normal).use {
            terminal.readInputEvent(TimeSource.Monotonic.markNow() + 1.seconds, MouseTracking.Normal)
            assertEquals("draft", terminal.readLineOrNull(true))
        }

        // Assert
        assertEquals(listOf("raw:Normal", "event:Normal", "line:true", "restore"), calls)
        assertSame(size, terminal.getTerminalSize())
        assertTrue(terminal.shouldAutoUpdateSize())
    }
}
