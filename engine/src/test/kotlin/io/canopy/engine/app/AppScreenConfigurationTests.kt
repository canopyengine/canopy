package io.canopy.engine.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import io.canopy.engine.core.managers.manager

class AppScreenConfigurationTests {
    private class TestApp : App<AppConfig>() {
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
    }

    private class RecordingScreen(private val entries: MutableList<String>, private val label: String) : Screen() {
        override fun onEnter() {
            entries += label
        }
    }

    @Test
    fun `an unconfigured app does not inherit an earlier app's screens`() {
        // Arrange
        val entries = mutableListOf<String>()
        val first = TestApp().apply { screens { start(RecordingScreen(entries, "first")) } }
        val second = TestApp()
        first.enter()
        first.exit()

        // Act
        second.enter()
        try {
            // Assert
            assertEquals(listOf("first"), entries)
            assertNull(manager<ScreenManager>().current)
        } finally {
            second.exit()
        }
    }

    @Test
    fun `apps configured before startup retain their own screen builders`() {
        // Arrange
        val entries = mutableListOf<String>()
        val first = TestApp().apply { screens { start(RecordingScreen(entries, "first")) } }
        val second = TestApp().apply { screens { start(RecordingScreen(entries, "second")) } }

        // Act
        first.enter()
        first.exit()
        second.enter()
        try {
            // Assert
            assertEquals(listOf("first", "second"), entries)
        } finally {
            second.exit()
        }
    }
}
