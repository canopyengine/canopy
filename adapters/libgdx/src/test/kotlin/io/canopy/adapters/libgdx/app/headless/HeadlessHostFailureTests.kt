package io.canopy.adapters.libgdx.app.headless

import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import com.badlogic.gdx.Gdx
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.core.managers.ManagersRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class HeadlessHostFailureTests {
    private class HostApp : App<AppConfig>() {
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) = HeadlessHost.launch(this)
    }

    @BeforeEach
    fun setup() = ManagersRegistry.exit()

    @AfterEach
    fun cleanup() {
        ManagersRegistry.exit()
        Gdx.app = null
        Gdx.graphics = null
        Gdx.input = null
        Gdx.files = null
        Gdx.net = null
    }

    @Test
    fun `real backend frame crash completes handle and cleans once without dispose callback`() = runBlocking {
        withTimeout(5.seconds) {
            val failure = IllegalStateException("frame")
            val closed = AtomicInteger()
            val crashed = CountDownLatch(1)
            val app = HostApp().apply {
                onEnter {
                    Thread.currentThread().uncaughtExceptionHandler =
                        Thread.UncaughtExceptionHandler { _, _ -> crashed.countDown() }
                }
                onUpdate { throw failure }
                onExit { closed.incrementAndGet() }
            }
            val handle = app.launchAsync()
            handle.awaitStarted()
            assertEquals(failure.message, assertFailsWith<IllegalStateException> { handle.join() }.message)
            assertTrue(crashed.await(2, TimeUnit.SECONDS))
            app.exit()
            assertEquals(1, closed.get())
        }
    }

    @Test
    fun `real backend startup crash fails both lifecycle waits once`() = runBlocking {
        withTimeout(5.seconds) {
            val failure = IllegalArgumentException("startup")
            val closed = AtomicInteger()
            val crashed = CountDownLatch(1)
            val app = HostApp().apply {
                onEnter {
                    Thread.currentThread().uncaughtExceptionHandler =
                        Thread.UncaughtExceptionHandler { _, _ -> crashed.countDown() }
                    throw failure
                }
                onExit { closed.incrementAndGet() }
            }
            val handle = app.launchAsync()
            assertFailsWith<IllegalArgumentException> { handle.awaitStarted() }
            assertFailsWith<IllegalArgumentException> { handle.join() }
            assertTrue(crashed.await(2, TimeUnit.SECONDS))
            app.exit()
            assertEquals(1, closed.get())
        }
    }
}
