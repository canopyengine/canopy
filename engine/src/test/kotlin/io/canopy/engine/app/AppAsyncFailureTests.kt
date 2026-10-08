package io.canopy.engine.app

import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class AppAsyncFailureTests {
    private class HostApp : App<AppConfig>() {
        var host: HostApp.() -> Unit = {
            engineLoop.enter()
            try {
                engineLoop.update(0f)
            } finally {
                engineLoop.exit()
            }
        }
        var beforeShutdown: () -> Unit = {}
        val uncaught = CountDownLatch(1)
        var threadFailure: Throwable? = null
        override fun defaultConfig() = AppConfig()
        override fun beforeExit() = beforeShutdown()
        override fun internalLaunch(config: AppConfig, vararg args: String) {
            Thread.currentThread().uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error ->
                threadFailure = error
                uncaught.countDown()
            }
            host()
        }
    }

    @BeforeEach
    fun setup() = ManagersRegistry.exit()

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `frame failure reaches join after successful cleanup`() = runBlocking {
        withTimeout(3.seconds) {
            val failure = IllegalStateException("frame")
            val closed = AtomicInteger()
            val app = HostApp().apply {
                onUpdate { throw failure }
                onExit { closed.incrementAndGet() }
            }
            val handle = app.launchAsync()
            handle.awaitStarted()
            assertEquals(failure.message, assertFailsWith<IllegalStateException> { handle.join() }.message)
            assertEquals(1, closed.get())
            assertTrue(app.uncaught.await(2, TimeUnit.SECONDS))
            assertSame(failure, app.threadFailure)
            app.exit()
            assertEquals(1, closed.get())
        }
    }

    @Test
    fun `runtime failure remains primary with shutdown failures in stage order`() = runBlocking {
        withTimeout(3.seconds) {
            val runtime = IllegalStateException("frame")
            val backend = IllegalArgumentException("backend")
            val manager = UnsupportedOperationException("manager")
            val application = IllegalAccessError("application")
            val stages = mutableListOf<String>()
            val app = HostApp().apply {
                managers {
                    +object : Manager {
                        override fun onExit() {
                            stages += "manager"
                            throw manager
                        }
                    }
                }
                beforeShutdown = {
                    stages += "backend"
                    throw backend
                }
                onExit {
                    stages += "application"
                    throw application
                }
                onUpdate { throw runtime }
            }
            val handle = app.launchAsync()
            assertFailsWith<IllegalStateException> { handle.join() }
            assertEquals(listOf("backend", "manager", "application"), stages)
            assertEquals(listOf(backend, manager, application), runtime.suppressed.toList())
            assertTrue(app.uncaught.await(2, TimeUnit.SECONDS))
            assertSame(runtime, app.threadFailure)
        }
    }

    @Test
    fun `async startup failure fails both waits and rolls back once`() = runBlocking {
        withTimeout(3.seconds) {
            val failure = IllegalArgumentException("startup")
            val closed = AtomicInteger()
            val app = HostApp().apply {
                onEnter { throw failure }
                onExit { closed.incrementAndGet() }
            }
            val handle = app.launchAsync()
            assertFailsWith<IllegalArgumentException> { handle.awaitStarted() }
            assertFailsWith<IllegalArgumentException> { handle.join() }
            assertTrue(app.uncaught.await(2, TimeUnit.SECONDS))
            app.exit()
            assertEquals(1, closed.get())
        }
    }

    @Test
    fun `escaped host error after entry is cleaned and propagated`() = runBlocking {
        withTimeout(3.seconds) {
            val failure = UnsupportedOperationException("host")
            val closed = AtomicInteger()
            val app = HostApp().apply {
                host = {
                    engineLoop.enter()
                    throw failure
                }
                onExit { closed.incrementAndGet() }
            }
            val handle = app.launchAsync()
            handle.awaitStarted()
            assertFailsWith<UnsupportedOperationException> { handle.join() }
            assertEquals(1, closed.get())
            assertTrue(app.uncaught.await(2, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `explicit host failure handoff precedes finally teardown`() = runBlocking {
        withTimeout(3.seconds) {
            val failure = IllegalStateException("host")
            val app = HostApp().apply {
                host = {
                    engineLoop.enter()
                    try {
                        throw failure
                    } catch (error: Throwable) {
                        engineLoop.reportFailure(error)
                        throw error
                    } finally {
                        engineLoop.exit()
                    }
                }
            }
            val handle = app.launchAsync()
            assertFailsWith<IllegalStateException> { handle.join() }
            assertTrue(app.uncaught.await(2, TimeUnit.SECONDS))
            assertSame(failure, app.threadFailure)
        }
    }

    @Test
    fun `graceful request during frame stops once after callback completion`() = runBlocking {
        withTimeout(3.seconds) {
            val frame = CountDownLatch(1)
            val stop = CountDownLatch(1)
            val closed = AtomicInteger()
            val app = HostApp().apply {
                host = {
                    installBackendHandle({ stop.countDown() })
                    engineLoop.enter()
                    try {
                        engineLoop.update(0f)
                    } finally {
                        engineLoop.exit()
                    }
                }
                onUpdate {
                    frame.countDown()
                    check(stop.await(2, TimeUnit.SECONDS))
                    assertEquals(0, closed.get())
                }
                onExit { closed.incrementAndGet() }
            }
            val handle = app.launchAsync()
            assertTrue(frame.await(2, TimeUnit.SECONDS))
            handle.requestExit()
            handle.requestExit()
            handle.join()
            app.exit()
            assertEquals(1, closed.get())
        }
    }

    @Test
    fun `unhandled host cancellation fails completion`() = runBlocking {
        withTimeout(3.seconds) {
            val failure = CancellationException("host cancellation")
            val app = HostApp().apply { onUpdate { throw failure } }
            val handle = app.launchAsync()
            assertFailsWith<CancellationException> { handle.join() }
            assertFalse(handle.join(1.seconds))
            assertTrue(app.uncaught.await(2, TimeUnit.SECONDS))
            assertSame(failure, app.threadFailure)
        }
    }

    @Test
    fun `cancelled waiters do not cancel application completion`() = runBlocking {
        withTimeout(3.seconds) {
            val app = HostApp()
            val joinReturned = AtomicBoolean()
            val startReturned = AtomicBoolean()
            val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                app.handle.join(10.seconds).also { joinReturned.set(true) }
            }
            waiter.cancel()
            assertFailsWith<CancellationException> { waiter.await() }
            assertFalse(joinReturned.get())
            val starter = async(start = CoroutineStart.UNDISPATCHED) {
                app.handle.awaitStarted(10.seconds).also { startReturned.set(true) }
            }
            starter.cancel()
            assertFailsWith<CancellationException> { starter.await() }
            assertFalse(startReturned.get())
            val handle = app.launchAsync()
            handle.awaitStarted()
            handle.join()
            assertTrue(handle.join(1.seconds))
        }
    }

    @Test
    fun `host failure before entry prevents later initialization`() = runBlocking {
        withTimeout(3.seconds) {
            val failure = IllegalStateException("host setup")
            val entries = AtomicInteger()
            val exits = AtomicInteger()
            val app = HostApp().apply {
                host = { throw failure }
                onEnter { entries.incrementAndGet() }
                onExit { exits.incrementAndGet() }
            }
            val handle = app.launchAsync()
            assertFailsWith<IllegalStateException> { handle.awaitStarted() }
            assertFailsWith<IllegalStateException> { handle.join() }
            assertTrue(app.uncaught.await(2, TimeUnit.SECONDS))
            app.enter()
            app.exit()
            assertEquals(0, entries.get())
            assertEquals(0, exits.get())
            assertNull(ManagersRegistry.getManagerOrNull(io.canopy.engine.core.managers.SceneManager::class))
        }
    }

    @Test
    fun `later host failure cannot replace retained frame failure or create suppression cycle`() = runBlocking {
        withTimeout(3.seconds) {
            val frame = IllegalStateException("frame")
            val host = UnsupportedOperationException("host")
            val app = HostApp().apply {
                onUpdate { throw frame }
                this.host = {
                    engineLoop.enter()
                    try {
                        engineLoop.update(0f)
                    } catch (_: IllegalStateException) {
                        throw host
                    }
                }
            }
            val handle = app.launchAsync()
            assertEquals(frame.message, assertFailsWith<IllegalStateException> { handle.join() }.message)
            assertTrue(app.uncaught.await(2, TimeUnit.SECONDS))
            assertSame(frame, app.threadFailure)
            assertEquals(listOf(host), frame.suppressed.toList())
            assertTrue(host.suppressed.isEmpty())
        }
    }

    @Test
    fun `timed waits return false for lifecycle errors while untimed waits propagate them`() = runBlocking {
        withTimeout(3.seconds) {
            val frameError = AssertionError("frame")
            val frameApp = HostApp().apply { onUpdate { throw frameError } }
            val frameHandle = frameApp.launchAsync()
            frameHandle.awaitStarted()
            assertFailsWith<AssertionError> { frameHandle.join() }
            assertFalse(frameHandle.join(1.seconds))
            assertTrue(frameApp.uncaught.await(2, TimeUnit.SECONDS))

            val startupError = AssertionError("startup")
            val startupApp = HostApp().apply { onEnter { throw startupError } }
            val startupHandle = startupApp.launchAsync()
            assertFailsWith<AssertionError> { startupHandle.awaitStarted() }
            assertFalse(startupHandle.awaitStarted(1.seconds))
            assertFalse(startupHandle.join(1.seconds))
            assertTrue(startupApp.uncaught.await(2, TimeUnit.SECONDS))
        }
    }
}
