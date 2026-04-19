package io.canopy.devtools.app

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

class CanopyAppTests {

    @Test
    fun `should launch async and close`() = runBlocking {
        val handle = testHeadlessApp {}.launchAsync()

        // Wait until the app has completed boot and the backend exit hooks are installed.
        assertTrue(
            handle.awaitStarted(2.seconds),
            "App didn't start within ${2.seconds} (backend hooks may not be installed)"
        )

        // Prefer graceful shutdown in tests; forceClose is a last resort.
        handle.requestExit()

        // If you want to be extra defensive in CI, you can fall back to forceClose on timeout:
        val exited = handle.join(2_000.milliseconds)
        if (!exited) {
            handle.forceClose()
            handle.join()
        }
    }
}
