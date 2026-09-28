package io.canopy.platforms.terminal.app

import io.canopy.devtools.app.appTestDriver
import io.canopy.engine.app.AppHandle
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

class TerminalAppTests {

    @Test
    fun `test terminal app should start and stop synchronously`() = runBlocking {
        val driver = appTestDriver(testTerminalApp {})

        driver.start()

        // Simulate a few frames
        repeat(3) { driver.frame(1f / 60f) }

        driver.stop()
    }

    @Test
    fun `test terminal app should handle resize`() = runBlocking {
        val driver = appTestDriver(testTerminalApp {})

        driver.start()
        driver.resize(120, 40)
        driver.frame(1f / 60f)
        driver.stop()
    }

    // This test requires a real terminal (Mordant) and will only pass in interactive environments
    // @Test
    // fun `terminal app async launch should start and shutdown gracefully`() = runBlocking {
    //     val handle: AppHandle = terminalApp {}.launchAsync()
    //
    //     assertTrue(handle.awaitStarted(5.seconds), "Terminal app didn't start within 5 seconds")
    //
    //     handle.requestExit()
    //     val exited = handle.join(2_000.milliseconds)
    //     if (!exited) {
    //         handle.forceClose()
    //         handle.join()
    //     }
    // }
}