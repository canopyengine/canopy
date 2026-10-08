package io.canopy.engine.app

import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class AppFailureTests {
    private class TestApp : App<AppConfig>() {
        var afterEntry: () -> Unit = {}
        var beforeShutdown: () -> Unit = {}
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
        override fun afterEnter() = afterEntry()
        override fun beforeExit() = beforeShutdown()
    }

    private class Service(private val enter: () -> Unit = {}, private val close: () -> Unit) : Manager {
        override fun onEnter() = enter()
        override fun onExit() = close()
    }

    @BeforeEach
    fun setup() = ManagersRegistry.exit()

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `failed startup rolls back managers and fails both completions before returning`() = runBlocking {
        val failure = IllegalStateException("startup")
        val cleanupFailure = IllegalArgumentException("cleanup")
        val events = mutableListOf<String>()
        val app = TestApp().apply {
            managers {
                +Service(close = {
                    events += "manager"
                    throw cleanupFailure
                })
            }
            onEnter { throw failure }
            onExit { events += "app" }
        }

        assertSame(failure, assertFailsWith<IllegalStateException> { app.enter() })
        assertEquals(listOf(cleanupFailure), failure.suppressed.toList())
        assertEquals(listOf("manager", "app"), events)
        assertNull(ManagersRegistry.getManagerOrNull(Service::class))
        assertEquals(failure.message, assertFailsWith<IllegalStateException> { app.handle.awaitStarted() }.message)
        assertEquals(failure.message, assertFailsWith<IllegalStateException> { app.handle.join() }.message)
        assertFalse(app.handle.awaitStarted(1.seconds))
        assertFalse(app.handle.join(1.seconds))
        app.enter()
        app.exit()
        assertEquals(2, events.size)
    }

    @Test
    fun `manager entry and after entry failures clean up the registered scope`() {
        listOf(true, false).forEach { failManager ->
            var closed = 0
            val failure = IllegalStateException("entry")
            val app = TestApp().apply {
                managers { +Service(enter = { if (failManager) throw failure }, close = { closed++ }) }
                afterEntry = { if (!failManager) throw failure }
            }
            assertSame(failure, assertFailsWith<IllegalStateException> { app.enter() })
            assertEquals(1, closed)
            assertNull(ManagersRegistry.getManagerOrNull(Service::class))
        }
    }

    @Test
    fun `partial builder failure cleans unentered managers and aggregates every shutdown failure`() = runBlocking {
        val startup = IllegalStateException("builder")
        val backend = IllegalArgumentException("backend")
        val manager = UnsupportedOperationException("manager")
        val application = IllegalAccessError("app")
        val events = mutableListOf<String>()
        val app = TestApp().apply {
            managers {
                +Service(enter = { events += "entered" }, close = {
                    events += "manager"
                    throw manager
                })
                throw startup
            }
            beforeShutdown = {
                events += "backend"
                throw backend
            }
            onExit {
                events += "app"
                throw application
            }
        }

        assertSame(startup, assertFailsWith<IllegalStateException> { app.enter() })
        assertEquals(listOf("backend", "manager", "app"), events)
        assertEquals(listOf(backend, manager, application), startup.suppressed.toList())
        assertNull(ManagersRegistry.getManagerOrNull(Service::class))
        assertEquals(startup.message, assertFailsWith<IllegalStateException> { app.handle.join() }.message)
        val next = TestApp()
        next.enter()
        next.exit()
        assertTrue(next.handle.join(1.seconds))
    }

    @Test
    fun `shutdown attempts all callbacks and preserves first failure`() = runBlocking {
        val first = IllegalArgumentException("backend")
        val managerFailure = IllegalStateException("manager")
        val appFailure = UnsupportedOperationException("app")
        val events = mutableListOf<String>()
        val app = TestApp().apply {
            managers {
                +Service(close = {
                    events += "manager"
                    throw managerFailure
                })
            }
            beforeShutdown = {
                events += "backend"
                throw first
            }
            onExit {
                events += "app"
                throw appFailure
            }
        }
        app.enter()

        assertSame(first, assertFailsWith<IllegalArgumentException> { app.exit() })
        assertEquals(listOf(managerFailure, appFailure), first.suppressed.toList())
        assertEquals(listOf("backend", "manager", "app"), events)
        assertNull(ManagersRegistry.getManagerOrNull(Service::class))
        assertTrue(app.handle.awaitStarted(1.seconds))
        assertFalse(app.handle.join(1.seconds))
        assertEquals(first.message, assertFailsWith<IllegalArgumentException> { app.handle.join() }.message)
        app.exit()
        assertEquals(3, events.size)
    }

    @Test
    fun `application exit callback failure is reflected by stopped completion`() = runBlocking {
        val failure = IllegalStateException("onExit")
        val app = TestApp().apply { onExit { throw failure } }
        app.enter()
        assertSame(failure, assertFailsWith<IllegalStateException> { app.exit() })
        assertFalse(app.handle.join(1.seconds))
        assertEquals(failure.message, assertFailsWith<IllegalStateException> { app.handle.join() }.message)
    }

    @Test
    fun `exit during frame is rejected before marking loop stopped`() = runBlocking {
        var closed = 0
        val app = TestApp().apply {
            managers { +Service(close = { closed++ }) }
            onUpdate {
                assertFailsWith<IllegalStateException> { exit() }
                assertFailsWith<IllegalStateException> { fail(IllegalArgumentException("nested")) }
            }
        }
        app.enter()
        app.update(0f)
        app.update(0f)
        assertEquals(0, closed)
        assertNotNull(ManagersRegistry.getManagerOrNull(Service::class))
        app.exit()
        assertEquals(1, closed)
        assertTrue(app.handle.join(1.seconds))
    }

    @Test
    fun `startup rejects nested entry and exit without repeating callbacks`() {
        var callbacks = 0
        val app = TestApp().apply {
            onEnter {
                callbacks++
                assertFailsWith<IllegalStateException> { enter() }
                assertFailsWith<IllegalStateException> { exit() }
            }
        }
        app.enter()
        app.exit()
        assertEquals(1, callbacks)
    }
}
