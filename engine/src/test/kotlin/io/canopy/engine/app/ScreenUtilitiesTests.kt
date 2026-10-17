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
    fun `shortcuts register replace navigate and remove through the current manager`() {
        val first = First(calls)
        app.registerScreen(first)
        assertNull(screens.current)
        assertEquals(emptyList(), calls)
        app.startScreen<First>()
        assertSame(first, screens.current)

        val replacement = First(calls, "replacement")
        app.registerScreen(replacement)
        assertNull(screens.current)
        app.startScreen<First>()
        assertSame(replacement, screens.current)
        app.removeScreen<Second>()
        assertSame(replacement, screens.current)

        app.removeScreen<First>()
        assertNull(screens.current)
        assertFailsWith<IllegalStateException> { app.startScreen<First>() }
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
