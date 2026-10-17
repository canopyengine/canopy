package io.canopy.platforms.terminal.app

import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalInterface
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.logging.LoggingPolicy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class TerminalInputShutdownTests {
    private class BlockingInput : TerminalInterface by Terminal(interactive = true).terminalInterface {
        val reading = CountDownLatch(1)
        val wake = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val closes = AtomicInteger()
        val tracking = AtomicReference<MouseTracking>()

        override fun enterRawMode(mouseTracking: MouseTracking): AutoCloseable {
            tracking.set(mouseTracking)
            return AutoCloseable {
                closes.incrementAndGet()
                closed.countDown()
            }
        }

        override fun readInputEvent(timeout: TimeMark, mouseTracking: MouseTracking): InputEvent? {
            reading.countDown()
            val remaining = -timeout.elapsedNow()
            val released = if (remaining.isInfinite()) {
                wake.await()
                true
            } else {
                wake.await(remaining.inWholeNanoseconds.coerceAtLeast(0), TimeUnit.NANOSECONDS)
            }
            return if (released) KeyboardEvent("x") else null
        }
    }

    @AfterEach
    fun cleanup() = ManagersRegistry.exit()

    @Test
    fun `requested exit closes an idle native input lease before shutdown callbacks`() = verifyShutdown(false)

    @Test
    fun `interrupted launch closes native input and preserves the interrupted status`() = verifyShutdown(true)

    private fun verifyShutdown(interrupt: Boolean) = runBlocking {
        // Arrange
        val input = BlockingInput()
        val terminal = Terminal(ansiLevel = AnsiLevel.TRUECOLOR, interactive = true, terminalInterface = input)
        val closedOnExit = AtomicBoolean()
        val interruptedOnExit = AtomicBoolean()
        val thread = AtomicReference<Thread>()
        val app = TerminalApp(terminal, {}, { Size(80, 24) }).apply {
            logging(LoggingPolicy.Host)
            onEnter { thread.set(Thread.currentThread()) }
            onExit {
                closedOnExit.set(input.closes.get() == 1)
                interruptedOnExit.set(Thread.currentThread().isInterrupted)
            }
        }
        val handle = app.launchAsync()
        try {
            withTimeout(5.seconds) {
                handle.awaitStarted()
                assertTrue(input.reading.await(2, TimeUnit.SECONDS), "Native read never started")
                assertEquals(
                    MouseTracking.Any,
                    input.tracking.get(),
                    "Raw reader must decode clicks, wheels and motion"
                )

                // Act
                if (interrupt) thread.get().interrupt() else handle.requestExit()
                handle.join()

                // Assert: no extra key should be needed to close an idle native read.
                assertTrue(closedOnExit.get(), "Input lease outlived application shutdown")
                assertEquals(interrupt, interruptedOnExit.get())
                assertEquals(1, input.closes.get())
            }
        } finally {
            input.wake.countDown()
            handle.requestExit()
            assertTrue(handle.join(5.seconds))
            assertTrue(input.closed.await(2, TimeUnit.SECONDS))
        }
    }
}
