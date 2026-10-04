package io.canopy.engine.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import io.canopy.engine.core.managers.ManagersRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class ScreenUtilitiesTests {
    private val calls = mutableListOf<String>()
    private lateinit var screens: ScreenManager
    private val app = object : App<AppConfig>() {
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
    }

    private open class RecordingScreen(private val calls: MutableList<String>, private val label: String) : Screen() {
        var duringInactive: () -> Unit = {}
        var duringExit: () -> Unit = {}
        override fun onEnter() {
            calls += "$label:enter"
        }
        override fun onActive() {
            calls += "$label:active"
        }
        override fun onInactive() {
            calls += "$label:inactive"
            duringInactive()
        }
        override fun onExit() {
            calls += "$label:exit"
            duringExit()
        }
    }

    private class First(calls: MutableList<String>, label: String = "first") : RecordingScreen(calls, label)
    private class Second(calls: MutableList<String>) : RecordingScreen(calls, "second")

    @BeforeEach
    fun setup() {
        ManagersRegistry.exit()
        screens = ScreenManager()
        ManagersRegistry.register(screens)
    }

    @AfterEach
    fun cleanup() {
        ManagersRegistry.exit()
    }

    @Test
    fun `runtime shortcuts register without entering and preserve navigation order and no-op`() {
        // Arrange
        val first = First(calls)
        app.registerScreen(first)
        app.registerScreen(Second(calls))
        assertNull(screens.current)
        assertEquals(emptyList(), calls)

        // Act
        app.startScreen<First>()
        app.startScreen<First>()
        app.startScreen<Second>()
        app.startScreen<First>()

        // Assert
        assertSame(first, screens.current)
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
    fun `missing target preserves active visit and removing it exits once`() {
        // Arrange
        val first = First(calls)
        app.registerScreen(first)
        app.startScreen<First>()
        calls.clear()

        // Act / Assert
        assertFailsWith<IllegalStateException> { app.startScreen<Second>() }
        app.removeScreen<Second>()
        assertSame(first, screens.current)
        assertEquals(emptyList(), calls)
        app.removeScreen<First>()
        app.removeScreen<First>()
        assertNull(screens.current)
        assertEquals(listOf("first:inactive", "first:exit"), calls)
        assertFailsWith<IllegalStateException> { app.startScreen<First>() }
    }

    @Test
    fun `registering replacement ends active visit and starts only explicitly`() {
        // Arrange
        val first = First(calls)
        val replacement = First(calls, "replacement")
        app.registerScreen(first)
        app.startScreen<First>()
        calls.clear()

        // Act / Assert
        app.registerScreen(first)
        assertSame(first, screens.current)
        assertEquals(emptyList(), calls)
        app.registerScreen(replacement)
        assertNull(screens.current)
        assertEquals(listOf("first:inactive", "first:exit"), calls)
        app.startScreen<First>()
        assertSame(replacement, screens.current)
        assertEquals(listOf("replacement:enter", "replacement:active"), calls.takeLast(2))
    }

    @Test
    fun `all shortcuts reject changes during inactive and exit callbacks`() {
        // Arrange
        val first = First(calls)
        val rejected = mutableListOf<String>()
        fun checkRestrictions(phase: String) {
            assertFailsWith<IllegalStateException> { app.registerScreen(First(calls, "replacement")) }
            assertFailsWith<IllegalStateException> { app.startScreen<Second>() }
            assertFailsWith<IllegalStateException> { app.removeScreen<Second>() }
            rejected += phase
        }
        first.duringInactive = { checkRestrictions("inactive") }
        first.duringExit = { checkRestrictions("exit") }
        app.registerScreen(first)
        val second = Second(calls)
        app.registerScreen(second)
        app.startScreen<First>()

        // Act
        app.startScreen<Second>()

        // Assert
        assertEquals(listOf("inactive", "exit"), rejected)
        assertSame(second, screens.current)
    }

    @Test
    fun `shortcuts require registered manager without creating one`() {
        // Arrange
        ManagersRegistry.unregister(ScreenManager::class)

        // Act / Assert
        assertFailsWith<IllegalStateException> { app.registerScreen(First(calls)) }
        assertFailsWith<IllegalStateException> { app.startScreen<First>() }
        assertFailsWith<IllegalStateException> { app.removeScreen<First>() }
        assertEquals(emptyList(), calls)
    }
}
