package io.canopy.engine.app.test

import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class CanopyAppTests {

    @Test
    fun `should launch async and close`() = runBlocking {
        val handle = testHeadlessApp {}.launchAsync()

        // Wait until the app actually started (exit hooks installed)
        check(handle.awaitStarted(500)) { "App didn't start in time" }

        handle.forceClose()
        handle.join()
    }
}
