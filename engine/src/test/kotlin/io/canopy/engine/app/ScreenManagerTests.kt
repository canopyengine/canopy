package io.canopy.engine.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.AfterEach

class ScreenManagerTests {
    private val calls = mutableListOf<String>()
    private val manager = ScreenManager()

    private open class RecordingScreen(val label: String, val calls: MutableList<String>) : Screen() {
        var enter: () -> Unit = {}
        var inactive: () -> Unit = {}
        var exit: () -> Unit = {}

        override fun onEnter() {
            calls += "$label:enter"
            enter()
        }

        override fun onActive() {
            calls += "$label:active"
        }

        override fun onInactive() {
            calls += "$label:inactive"
            inactive()
        }

        override fun onExit() {
            calls += "$label:exit"
            exit()
        }

        override fun onUpdate(delta: Float) {
            calls += "$label:update:$delta"
        }

        override fun onPhysicsUpdate(delta: Float) {
            calls += "$label:physics:$delta"
        }

        override fun onResize(width: Int, height: Int) {
            calls += "$label:resize:$width,$height"
        }
    }

    private class First(calls: MutableList<String>, label: String = "first") : RecordingScreen(label, calls)
    private class Second(calls: MutableList<String>) : RecordingScreen("second", calls)

    @AfterEach
    fun cleanup() {
        manager.onExit()
    }

    @Test
    fun `navigation brackets every visit and starting current is a no-op`() {
        // Arrange
        manager.register(First(calls))
        manager.register(Second(calls))

        // Act
        manager.start(First::class)
        manager.start(First::class)
        manager.start(Second::class)
        manager.start(First::class)

        // Assert
        assertEquals(
            listOf(
                "first:enter", "first:active", "first:inactive", "first:exit",
                "second:enter", "second:active", "second:inactive", "second:exit",
                "first:enter", "first:active"
            ),
            calls
        )
    }

    @Test
    fun `shutdown exits only the active visit once and clears registrations`() {
        // Arrange
        manager.register(First(calls))
        manager.register(Second(calls))
        manager.start(First::class)
        calls.clear()

        // Act
        manager.onExit()
        manager.onExit()

        // Assert
        assertEquals(listOf("first:inactive", "first:exit"), calls)
        assertNull(manager.current)
        assertFailsWith<IllegalStateException> { manager.start(First::class) }
        assertFailsWith<IllegalStateException> { manager.start(Second::class) }
    }

    @Test
    fun `shutdown does not repeat cleanup of previous visits`() {
        // Arrange
        manager.register(First(calls))
        manager.register(Second(calls))
        manager.start(First::class)
        manager.start(Second::class)
        calls.clear()

        // Act
        manager.onExit()

        // Assert
        assertEquals(listOf("second:inactive", "second:exit"), calls)
    }

    @Test
    fun `replacement ends an active visit and starts only on request`() {
        // Arrange
        val first = First(calls)
        val replacement = First(calls, "replacement")
        manager.register(first)
        manager.start(First::class)
        calls.clear()

        // Act
        manager.register(first)
        assertSame(first, manager.current)
        assertEquals(emptyList(), calls)
        manager.register(replacement)

        // Assert
        assertNull(manager.current)
        assertEquals(listOf("first:inactive", "first:exit"), calls)
        manager.start(First::class)
        assertSame(replacement, manager.current)
        assertEquals("replacement:active", calls.last())
    }

    @Test
    fun `removing an inactive registration leaves the active screen alone`() {
        // Arrange
        val first = First(calls)
        manager.register(first)
        manager.register(Second(calls))
        manager.start(First::class)
        calls.clear()

        // Act
        manager.remove(Second::class)
        manager.remove(Second::class)

        // Assert
        assertSame(first, manager.current)
        assertEquals(emptyList(), calls)
        manager.remove(First::class)
        manager.remove(First::class)
        assertNull(manager.current)
        assertEquals(listOf("first:inactive", "first:exit"), calls)
    }

    @Test
    fun `unregistered navigation fails before ending the current visit`() {
        // Arrange
        val first = First(calls)
        manager.register(first)
        manager.start(First::class)
        calls.clear()

        // Act
        assertFailsWith<IllegalStateException> { manager.start(Second::class) }

        // Assert
        assertSame(first, manager.current)
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `frame physics and resize callbacks reach only the active screen`() {
        // Arrange
        manager.register(First(calls))
        manager.register(Second(calls))
        manager.start(Second::class)
        calls.clear()

        // Act
        manager.onUpdate(0.5f)
        manager.onPhysicsUpdate(0.1f)
        manager.onResize(80, 24)

        // Assert
        assertEquals(listOf("second:update:0.5", "second:physics:0.1", "second:resize:80,24"), calls)
        manager.onExit()
        calls.clear()
        manager.onUpdate(1f)
        manager.onPhysicsUpdate(1f)
        manager.onResize(1, 1)
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `enter redirect does not activate a screen that already left`() {
        // Arrange
        val first = First(calls)
        val second = Second(calls)
        first.enter = { manager.start(Second::class) }
        manager.register(first)
        manager.register(second)

        // Act
        manager.start(First::class)

        // Assert
        assertSame(second, manager.current)
        assertEquals(
            listOf("first:enter", "first:inactive", "first:exit", "second:enter", "second:active"),
            calls
        )
    }

    @Test
    fun `round trip redirect activates the new visit to the same instance only once`() {
        // Arrange
        val first = First(calls)
        val second = Second(calls)
        first.enter = {
            first.enter = {}
            manager.start(Second::class)
        }
        second.enter = { manager.start(First::class) }
        manager.register(first)
        manager.register(second)

        // Act
        manager.start(First::class)

        // Assert
        assertSame(first, manager.current)
        assertEquals(1, calls.count { it == "first:active" })
        assertEquals(0, calls.count { it == "second:active" })
    }

    @Test
    fun `leaving hooks cannot start remove or register screens`() {
        // Arrange
        val first = First(calls)
        val second = Second(calls)
        val assertRejected = {
            assertNull(manager.current)
            assertFailsWith<IllegalStateException> { manager.start(Second::class) }
            assertFailsWith<IllegalStateException> { manager.remove(Second::class) }
            assertFailsWith<IllegalStateException> { manager.register(second) }
            Unit
        }
        first.inactive = assertRejected
        first.exit = assertRejected
        manager.register(first)
        manager.register(second)
        manager.start(First::class)

        // Act
        manager.start(Second::class)

        // Assert
        assertSame(second, manager.current)
    }

    @Test
    fun `inactive failure still exits once and shutdown clears registrations`() {
        // Arrange
        val first = First(calls)
        val failure = IllegalStateException("inactive failed")
        first.inactive = { throw failure }
        manager.register(first)
        manager.start(First::class)
        calls.clear()

        // Act
        assertSame(failure, assertFailsWith<IllegalStateException> { manager.onExit() })
        manager.onExit()

        // Assert
        assertNull(manager.current)
        assertEquals(listOf("first:inactive", "first:exit"), calls)
        assertFailsWith<IllegalStateException> { manager.start(First::class) }
    }

    @Test
    fun `both leaving failures preserve the inactive failure and still clear the visit`() {
        // Arrange
        val first = First(calls)
        val inactiveFailure = IllegalStateException("inactive")
        val exitFailure = IllegalStateException("exit")
        first.inactive = { throw inactiveFailure }
        first.exit = { throw exitFailure }
        manager.register(first)
        manager.start(First::class)
        calls.clear()

        // Act
        val failure = assertFailsWith<IllegalStateException> { manager.onExit() }
        manager.onExit()

        // Assert
        assertSame(inactiveFailure, failure)
        assertEquals(listOf(exitFailure), failure.suppressed.toList())
        assertNull(manager.current)
        assertEquals(listOf("first:inactive", "first:exit"), calls)
    }

    @Test
    fun `exit failure does not leave a current screen or cause repeated cleanup`() {
        // Arrange
        val first = First(calls)
        first.exit = { error("exit failed") }
        manager.register(first)
        manager.start(First::class)
        calls.clear()

        // Act
        assertFailsWith<IllegalStateException> { manager.onExit() }
        manager.onExit()

        // Assert
        assertNull(manager.current)
        assertEquals(listOf("first:inactive", "first:exit"), calls)
    }
}
