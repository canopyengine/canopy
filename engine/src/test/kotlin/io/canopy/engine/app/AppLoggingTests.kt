package io.canopy.engine.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.logging.LoggingPolicy
import io.canopy.engine.logging.LoggingSession
import org.junit.jupiter.api.AfterEach

class AppLoggingTests {
    private class TestApp : App<AppConfig>() {
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
    }

    private class Session : LoggingSession {
        var depth = 0
        var ends = 0
        var closes = 0
        var outcome: Pair<String, Throwable?>? = null
        var finish: () -> Unit = {}
        var release: () -> Unit = {}
        var beforeContext: () -> Unit = {}
        var afterContext: () -> Unit = {}

        override fun <T> withContext(block: () -> T): T {
            depth++
            try {
                beforeContext()
                return block().also { afterContext() }
            } finally {
                depth--
            }
        }

        override fun end(reason: String, failure: Throwable?) {
            ends++
            outcome = reason to failure
            finish()
        }

        override fun close() {
            closes++
            release()
        }
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `session scopes all callbacks including exit and clears before close`() {
        // Arrange
        val session = Session()
        val app = TestApp()
        val callbacks = mutableListOf<String>()
        app.logging(LoggingPolicy { session })
        app.onEnter {
            assertEquals(1, session.depth)
            callbacks += "enter"
        }
        app.onUpdate {
            assertEquals(1, session.depth)
            callbacks += "frame"
        }
        app.onPhysicsUpdate {
            assertEquals(1, session.depth)
            callbacks += "physics"
        }
        app.onResize { _, _ ->
            assertEquals(1, session.depth)
            callbacks += "resize"
        }
        app.onExit {
            assertEquals(1, session.depth)
            callbacks += "exit"
        }
        session.release = { app.withLoggingContext { assertEquals(0, session.depth) } }

        // Act
        app.enter()
        app.update(0f)
        app.physicsUpdate(0.01f)
        app.resize(100, 80)
        var invocations = 0
        val nullable: String? = app.withLoggingContext {
            invocations++
            null
        }
        assertEquals(null, nullable)
        app.exit()
        app.exit()

        // Assert
        assertEquals(listOf("enter", "frame", "physics", "resize", "exit"), callbacks)
        assertEquals(1, invocations)
        assertEquals(0, session.depth)
        assertEquals(1, session.ends)
        assertEquals("normal" to null, session.outcome)
        assertEquals(1, session.closes)
        assertFailsWith<IllegalStateException> { app.logging(LoggingPolicy.Host) }
    }

    @Test
    fun `startup failure preserves original failure and attempts end exit and close`() {
        // Arrange
        val startup = IllegalStateException("startup")
        val end = IllegalArgumentException("end")
        val exit = UnsupportedOperationException("exit")
        val close = IllegalAccessError("close")
        val session = Session().also {
            it.finish = { throw end }
            it.release = { throw close }
        }
        val app = TestApp().apply {
            logging(LoggingPolicy { session })
            onEnter { throw startup }
            onExit { throw exit }
        }

        // Act
        val failure = assertFailsWith<IllegalStateException> { app.enter() }
        app.exit()

        // Assert
        assertSame(startup, failure)
        assertEquals(listOf(end, exit, close), failure.suppressed.toList())
        assertEquals("crash" to startup, session.outcome)
        assertEquals(1, session.ends)
        assertEquals(1, session.closes)
        assertEquals(0, session.depth)
    }

    @Test
    fun `policy startup failure does not close a session belonging to another app`() {
        // Arrange
        val firstSession = Session()
        val first = TestApp().apply { logging(LoggingPolicy { firstSession }) }
        first.enter()
        val startup = IllegalStateException("logging startup")
        var exits = 0
        val second = TestApp().apply {
            logging(LoggingPolicy { throw startup })
            onExit { exits++ }
        }

        // Act
        assertSame(startup, assertFailsWith<IllegalStateException> { second.enter() })
        second.exit()

        // Assert
        assertEquals(0, firstSession.ends)
        assertEquals(0, firstSession.closes)
        first.withLoggingContext { assertEquals(1, firstSession.depth) }
        first.exit()
        assertEquals(1, firstSession.ends)
        assertEquals(1, firstSession.closes)
        assertEquals(1, exits)
    }

    @Test
    fun `teardown attempts cleanup in host context when session scope setup fails`() {
        // Arrange
        val scopeFailure = IllegalStateException("scope setup")
        val endFailure = IllegalArgumentException("end")
        var managerExits = 0
        var appExits = 0
        val session = Session().also { it.finish = { throw endFailure } }
        val service = object : Manager {
            override fun onExit() {
                managerExits++
                assertEquals(0, session.depth)
            }
        }
        val app = TestApp().apply {
            logging(LoggingPolicy { session })
            managers { +service }
            onExit {
                appExits++
                assertEquals(0, session.depth)
            }
        }
        app.enter()
        session.beforeContext = { throw scopeFailure }

        // Act
        val failure = assertFailsWith<IllegalStateException> { app.exit() }
        app.exit()

        // Assert
        assertSame(scopeFailure, failure)
        assertEquals(listOf(endFailure), failure.suppressed.toList())
        assertEquals(1, managerExits)
        assertEquals(1, appExits)
        assertEquals(1, session.ends)
        assertEquals(1, session.closes)
        assertEquals(0, session.depth)
        assertEquals(null, ManagersRegistry.getManagerOrNull(service::class))
    }

    @Test
    fun `teardown does not repeat cleanup when session scope restoration fails after each block`() {
        // Arrange
        val scopeFailure = IllegalStateException("scope restoration")
        var managerExits = 0
        var appExits = 0
        val session = Session()
        val service = object : Manager {
            override fun onExit() {
                managerExits++
                assertEquals(1, session.depth)
            }
        }
        val app = TestApp().apply {
            logging(LoggingPolicy { session })
            managers { +service }
            onExit {
                appExits++
                assertEquals(1, session.depth)
            }
        }
        app.enter()
        session.afterContext = { throw scopeFailure }

        // Act
        val failure = assertFailsWith<IllegalStateException> { app.exit() }
        app.exit()

        // Assert
        assertSame(scopeFailure, failure)
        assertEquals(emptyList(), failure.suppressed.toList())
        assertEquals(1, managerExits)
        assertEquals(1, appExits)
        assertEquals(1, session.ends)
        assertEquals(1, session.closes)
        assertEquals(0, session.depth)
        assertEquals(null, ManagersRegistry.getManagerOrNull(service::class))
    }

    @Test
    fun `reusable policy opens an independent session for each sequential app`() {
        // Arrange
        val sessions = mutableListOf<Session>()
        val versions = mutableListOf<String>()
        val policy = LoggingPolicy { version ->
            versions += version
            Session().also { sessions += it }
        }
        val first = TestApp().apply { logging(policy) }
        val second = TestApp().apply { logging(policy) }

        // Act
        first.enter()
        first.exit()
        second.enter()
        assertEquals(0, sessions.last().closes)
        second.exit()

        // Assert
        assertEquals(2, sessions.size)
        assertTrue(sessions.first() !== sessions.last())
        assertTrue(versions.all { it.isNotBlank() })
        assertTrue(sessions.all { it.ends == 1 && it.closes == 1 && it.depth == 0 })
    }
}
