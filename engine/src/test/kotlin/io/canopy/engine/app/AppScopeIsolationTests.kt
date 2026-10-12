package io.canopy.engine.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import io.canopy.engine.core.managers.ExecutionMode
import io.canopy.engine.core.managers.GameManager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager

class AppScopeIsolationTests {
    private class TestApp : App<AppConfig>() {
        var hostStarts = 0
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) {
            hostStarts++
            engineLoop.enter()
            engineLoop.exit()
        }
    }
    private class TestScreen : Screen()

    @Test
    fun `the app reservation and diagnostic mode remain active through shutdown callbacks`() {
        // Arrange
        val first = TestApp().apply { executionMode = ExecutionMode.Debug }
        val second = TestApp()
        var checked = false
        first.onExit {
            assertEquals(ExecutionMode.Debug, GameManager.executionMode)
            assertFailsWith<IllegalStateException> { second.enter() }
            checked = true
        }
        first.enter()

        // Act
        first.exit()

        // Assert
        assertEquals(true, checked)
        val next = TestApp()
        next.enter()
        next.exit()
    }

    @Test
    fun `screen shortcuts on another app cannot mutate the running app`() {
        // Arrange
        val first = TestApp()
        val inactive = TestApp()
        val screen = TestScreen()
        first.screens { start(screen) }
        first.enter()
        try {
            // Act / Assert
            assertFailsWith<IllegalStateException> { inactive.registerScreen(TestScreen()) }
            assertFailsWith<IllegalStateException> { inactive.startScreen<TestScreen>() }
            assertFailsWith<IllegalStateException> { inactive.removeScreen<TestScreen>() }
            assertSame(screen, manager<ScreenManager>().current)
        } finally {
            first.exit()
        }
    }

    @Test
    fun `a second app cannot replace a running app's managers`() {
        // Arrange
        val first = TestApp()
        val second = TestApp()
        var updates = 0
        first.onUpdate { updates++ }
        first.enter()
        val scenes = manager<SceneManager>()
        try {
            // Act / Assert
            assertFailsWith<IllegalStateException> { second.enter() }
            assertSame(scenes, manager<SceneManager>())
            first.update(0f)
            assertEquals(1, updates)
        } finally {
            second.exit()
            first.exit()
        }
        val next = TestApp()
        next.enter()
        next.exit()
    }

    @Test
    fun `a rejected launch does not start another platform host`() {
        // Arrange
        val first = TestApp()
        val second = TestApp()
        first.enter()
        val scenes = manager<SceneManager>()
        try {
            // Act / Assert
            assertFailsWith<IllegalStateException> { second.launch() }
            assertEquals(0, second.hostStarts)
            assertSame(scenes, manager<SceneManager>())
        } finally {
            second.exit()
            first.exit()
        }
    }

    @Test
    fun `launching an entered app again does not stop its existing runtime`() {
        // Arrange
        val app = TestApp()
        app.enter()
        val scenes = manager<SceneManager>()
        try {
            // Act / Assert
            assertFailsWith<IllegalStateException> { app.launch() }
            assertFailsWith<IllegalStateException> { app.launchAsync() }
            assertEquals(0, app.hostStarts)
            assertSame(scenes, manager<SceneManager>())
            app.update(0f)
        } finally {
            app.exit()
        }
    }

    @Test
    fun `diagnostic mode belongs to its app and does not leak into the next app`() {
        // Arrange
        val first = TestApp().apply { executionMode = ExecutionMode.Debug }
        first.enter()
        try {
            assertEquals(ExecutionMode.Debug, GameManager.executionMode)
            GameManager.executionMode = ExecutionMode.Normal
            assertEquals(ExecutionMode.Normal, first.executionMode)
            first.executionMode = ExecutionMode.Debug
        } finally {
            first.exit()
        }

        // Act
        val next = TestApp()
        next.enter()
        try {
            // Assert
            assertEquals(ExecutionMode.Normal, next.executionMode)
            assertEquals(ExecutionMode.Normal, GameManager.executionMode)
            assertEquals(ExecutionMode.Debug, first.executionMode)
        } finally {
            next.exit()
        }
    }

    @Test
    fun `standalone scope replacement cannot tear down a running app`() {
        // Arrange
        val app = TestApp()
        app.enter()
        val scenes = manager<SceneManager>()
        try {
            // Act / Assert
            assertFailsWith<IllegalStateException> { ManagersRegistry.withScope {} }
            assertSame(scenes, manager<SceneManager>())
        } finally {
            app.exit()
        }
    }
}
