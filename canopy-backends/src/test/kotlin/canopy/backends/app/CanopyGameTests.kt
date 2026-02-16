package canopy.backends.app

import canopy.backends.test.TestHeadlessCanopyGame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue

class CanopyGameTests {
    @Test
    fun `should bootstrap a test variant of a canopy game`() = runBlocking {
        val created = CompletableDeferred<Unit>()
        val disposed = CompletableDeferred<Unit>()

        val canopyGame = TestHeadlessCanopyGame(
            onCreate = { created.complete(Unit) },
            onDispose = { disposed.complete(Unit) },
        )

        val handle = canopyGame.launch()

        withTimeout(2_000) { created.await() }

        handle.exit()

        withTimeout(2_000) { disposed.await() }
    }

}
