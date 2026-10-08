package io.canopy.platforms.terminal.experiment

import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import io.canopy.engine.app.AppConfig
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class JvmHeadlessHostTests {
    private class Clock {
        var now = 0L
        val waits = mutableListOf<Long>()
        val host = JvmHeadlessHost({ now }, { nanos ->
            waits += nanos
            now += nanos
        })
    }

    @BeforeEach
    fun setup() {
        Thread.interrupted()
        ManagersRegistry.exit()
    }

    @AfterEach
    fun cleanup() {
        Thread.interrupted()
        ManagersRegistry.exit()
    }

    @Test
    fun `lifecycle stays on caller and fixed physics uses elapsed seconds`() = runBlocking {
        val clock = Clock()
        val caller = Thread.currentThread()
        val events = mutableListOf<String>()
        val frames = mutableListOf<Float>()
        val physics = mutableListOf<Float>()
        val app = JvmHeadlessApp(clock.host).apply {
            config(AppConfig(fps = 50))
            onEnter {
                assertSame(caller, Thread.currentThread())
                events += "enter"
            }
            onPhysicsUpdate {
                assertSame(caller, Thread.currentThread())
                physics += it
            }
            onUpdate {
                assertSame(caller, Thread.currentThread())
                frames += it
                if (frames.size == 3) handle.requestExit()
            }
            onExit {
                assertSame(caller, Thread.currentThread())
                events += "exit"
            }
        }

        app.launch()

        assertEquals(listOf("enter", "exit"), events)
        assertEquals(listOf(0f, .02f, .02f), frames)
        assertEquals(listOf(1f / 60f, 1f / 60f), physics)
        assertEquals(listOf(20_000_000L, 20_000_000L), clock.waits)
        assertTrue(app.handle.awaitStarted(1.seconds))
        assertTrue(app.handle.join(1.seconds))
    }

    @Test
    fun `paused callbacks get zero while managers remain driven and resume discards remainder`() {
        val clock = Clock()
        val frames = mutableListOf<Float>()
        val physics = mutableListOf<Float>()
        val managerFrames = mutableListOf<Float>()
        val managerPhysics = mutableListOf<Float>()
        var ticks = 0
        val app = JvmHeadlessApp(clock.host).apply {
            config(AppConfig(fps = 50))
            managers {
                +object : Manager {
                    override fun onUpdate(delta: Float) {
                        managerFrames += delta
                    }
                    override fun onPhysicsUpdate(delta: Float) {
                        managerPhysics += delta
                    }
                }
            }
            onEnter { pause() }
            onPhysicsUpdate { physics += it }
            onUpdate {
                frames += it
                if (++ticks == 2) resume()
                if (ticks == 3) handle.requestExit()
            }
        }

        app.launch()

        assertEquals(listOf(0f, 0f, .02f), frames)
        assertEquals(listOf(1f / 60f), physics)
        assertEquals(listOf(0f, .02f, .02f), managerFrames)
        assertEquals(listOf(1f / 60f), managerPhysics)
        assertEquals(1L, app.frameCount)
    }

    @Test
    fun `entry stop suppresses every frame and force close stops without another sleep`() {
        listOf(false, true).forEach { stopInFrame ->
            val clock = Clock()
            var frames = 0
            var exits = 0
            val app = JvmHeadlessApp(clock.host).apply {
                onEnter { if (!stopInFrame) handle.requestExit() }
                onUpdate {
                    frames++
                    handle.forceClose()
                }
                onExit { exits++ }
            }

            app.launch()

            assertEquals(if (stopInFrame) 1 else 0, frames)
            assertEquals(1, exits)
            assertTrue(clock.waits.isEmpty())
            assertFalse(Thread.currentThread().isInterrupted)
        }
    }

    @Test
    fun `deadlines subtract frame work and overruns do not sleep or add catch up frames`() {
        val clock = Clock()
        val frames = mutableListOf<Float>()
        val app = JvmHeadlessApp(clock.host).apply {
            config(AppConfig(fps = 10))
            onUpdate {
                frames += it
                clock.now += if (frames.size == 1) 30_000_000L else 150_000_000L
                if (frames.size == 3) handle.requestExit()
            }
        }

        app.launch()

        assertEquals(listOf(70_000_000L), clock.waits)
        assertEquals(listOf(0f, .1f, .15f), frames)
    }

    @Test
    fun `deadline arithmetic survives nanoTime wraparound`() {
        val clock = Clock().apply { now = Long.MAX_VALUE - 5_000_000L }
        val frames = mutableListOf<Float>()
        val app = JvmHeadlessApp(clock.host).apply {
            config(AppConfig(fps = 100))
            onUpdate {
                frames += it
                if (frames.size == 2) handle.requestExit()
            }
        }

        app.launch()

        assertEquals(listOf(10_000_000L), clock.waits)
        assertEquals(listOf(0f, .01f), frames)
    }

    @Test
    fun `preexisting interrupt completes entry and teardown without a frame`() = runBlocking {
        val clock = Clock()
        var entries = 0
        var exits = 0
        val app = JvmHeadlessApp(clock.host).apply {
            onEnter { entries++ }
            onUpdate { fail("No interrupted frame") }
            onExit { exits++ }
        }
        Thread.currentThread().interrupt()

        app.launch()

        assertTrue(Thread.currentThread().isInterrupted)
        Thread.interrupted()
        assertEquals(1, entries)
        assertEquals(1, exits)
        assertTrue(clock.waits.isEmpty())
        assertTrue(app.handle.awaitStarted(1.seconds))
        assertTrue(app.handle.join(1.seconds))
    }

    @Test
    fun `sleep interruption restores flag before cleanup and exits once`() {
        var waits = 0
        var exits = 0
        val host = JvmHeadlessHost({ 0L }, {
            waits++
            throw InterruptedException("wake")
        })
        val app = JvmHeadlessApp(host).apply {
            onExit {
                assertTrue(Thread.currentThread().isInterrupted)
                exits++
            }
        }

        app.launch()

        assertEquals(1, waits)
        assertEquals(1, exits)
        assertTrue(Thread.currentThread().isInterrupted)
    }

    @Test
    fun `failed entry rolls back exactly once and fails both completions`() = runBlocking {
        val primary = IllegalStateException("enter")
        var managerCloses = 0
        var exits = 0
        val app = JvmHeadlessApp(Clock().host).apply {
            managers {
                +object : Manager {
                    override fun onExit() {
                        managerCloses++
                    }
                }
            }
            onEnter { throw primary }
            onExit { exits++ }
        }

        assertSame(primary, assertFailsWith<IllegalStateException> { app.launch() })

        assertEquals(1, managerCloses)
        assertEquals(1, exits)
        assertEquals(primary.message, assertFailsWith<IllegalStateException> { app.handle.awaitStarted() }.message)
        assertEquals(primary.message, assertFailsWith<IllegalStateException> { app.handle.join() }.message)
    }

    @Test
    fun `frame failure stays primary and cleanup failures are suppressed in order`() {
        val primary = IllegalStateException("frame")
        val managerFailure = IllegalArgumentException("manager")
        val exitFailure = UnsupportedOperationException("exit")
        val events = mutableListOf<String>()
        val app = JvmHeadlessApp(Clock().host).apply {
            managers {
                +object : Manager {
                    override fun onExit() {
                        events += "manager"
                        throw managerFailure
                    }
                }
            }
            onUpdate { throw primary }
            onExit {
                events += "exit"
                throw exitFailure
            }
        }

        assertSame(primary, assertFailsWith<IllegalStateException> { app.launch() })

        assertEquals(listOf("manager", "exit"), events)
        assertEquals(listOf(managerFailure), primary.suppressed.toList())
        assertEquals(listOf(exitFailure), managerFailure.suppressed.toList())
    }

    @Test
    fun `same error from frame and teardown avoids self suppression`() {
        val primary = IllegalStateException("shared")
        val app = JvmHeadlessApp(Clock().host).apply {
            onUpdate { throw primary }
            onExit { throw primary }
        }

        assertSame(primary, assertFailsWith<IllegalStateException> { app.launch() })
        assertTrue(primary.suppressed.isEmpty())
    }

    @Test
    fun `invalid fps rejects launch before lifecycle starts`() {
        listOf(0, -1, 1_000_000_001).forEach { fps ->
            var entered = false
            val app = JvmHeadlessApp(Clock().host).apply {
                config(AppConfig(fps = fps))
                onEnter { entered = true }
            }
            assertFailsWith<IllegalArgumentException> { app.launch() }
            assertFalse(entered)
        }
    }

    @Test
    fun `retained handle cannot interrupt caller after synchronous host finishes`() {
        val app = JvmHeadlessApp(Clock().host).apply {
            onEnter { handle.requestExit() }
        }
        app.launch()

        val requester = Thread({
            app.handle.requestExit()
            app.handle.forceClose()
        }, "late-stop")
        requester.start()
        requester.join(5_000)

        assertFalse(requester.isAlive)
        assertFalse(Thread.currentThread().isInterrupted)
    }
}
