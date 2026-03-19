package io.canopy.devtools.app

import kotlin.test.Test
import kotlin.test.assertTrue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

class CanopyAppTests {

    @Test
    fun `should launch async and close`() = runBlocking {
        val handle = testHeadlessApp {}.launchAsync()

        // Wait until the app has completed boot and the backend exit hooks are installed.
        assertTrue(
            handle.awaitStarted(500, TimeUnit.MILLISECONDS),
            "App didn't start within 500ms (backend hooks may not be installed)"
        )

        // Prefer graceful shutdown in tests; forceClose is a last resort.
        handle.requestExit()

        // If you want to be extra defensive in CI, you can fall back to forceClose on timeout:
        val exited = handle.join(2_000, TimeUnit.MILLISECONDS)
        if (!exited) {
            handle.forceClose()
            handle.join()
        }
    }
}
