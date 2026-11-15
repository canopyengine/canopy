package io.canopy.tooling.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

class DiagnosticMessagesTests {
    private class RecordingCollector : MessageCollector {
        val messages = mutableListOf<Pair<CompilerMessageSeverity, String>>()
        override fun clear() = messages.clear()
        override fun hasErrors(): Boolean = messages.any { it.first.isError }
        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?,
        ) {
            messages += severity to message
        }
    }

    @Test
    fun `diagnostics remain buffered while error counts and deduplication are available`() {
        // Arrange
        val collector = RecordingCollector()
        val diagnostics = DiagnosticMessages(collector)

        // Act
        diagnostics.collect {
            diagnostics.emit("FIRST", "First problem")
            diagnostics.emit("FIRST", "First problem")
            diagnostics.emit("SECOND", "Second problem")
            diagnostics.emit("WARNING", "Warning", warning = true)
            assertEquals(2, diagnostics.errorCount)
            assertTrue(collector.messages.isEmpty())
        }

        // Assert
        assertEquals(3, collector.messages.size)
        assertEquals(2, collector.messages.count { it.first.isError })
        assertEquals(CompilerMessageSeverity.WARNING, collector.messages.last().first)
    }

    @Test
    fun `collected errors survive a failed pass and collection state is released`() {
        // Arrange
        val collector = RecordingCollector()
        val diagnostics = DiagnosticMessages(collector)

        // Act
        assertFailsWith<IllegalStateException> {
            diagnostics.collect {
                diagnostics.emit("FIRST", "First problem")
                error("Pass failed")
            }
        }
        diagnostics.collect { diagnostics.emit("SECOND", "Second problem") }

        // Assert
        assertEquals(2, collector.messages.size)
        assertTrue("FIRST" in collector.messages.first().second)
        assertTrue("SECOND" in collector.messages.last().second)
    }
}
