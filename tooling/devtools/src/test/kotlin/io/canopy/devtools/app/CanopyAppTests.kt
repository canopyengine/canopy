package io.canopy.devtools.app

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

class CanopyAppTests {

    @Test
    fun `should launch async and close`() = runBlocking {
        val handle = testHeadlessApp {}.launchAsync()

        try {
            assertTrue(handle.awaitStarted(10.seconds), "App did not start")
            handle.requestExit()
            assertTrue(handle.join(5.seconds), "App did not stop gracefully")
        } finally {
            handle.forceClose()
            assertTrue(handle.join(5.seconds), "App cleanup did not complete")
        }
    }
}
