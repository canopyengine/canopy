package canopy.backends.app

import canopy.backends.test.TestHeadlessCanopyGame
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue

class CanopyGameTests {
    @Test
    fun `should bootstrap a test variant of a canopy game`() =
        runBlocking {
            var createCalled = false
            var disposeCalled = false

            val canopyGame =
                TestHeadlessCanopyGame(
                    onCreate = { createCalled = true },
                    onDispose = { disposeCalled = true },
                )

            val handle = canopyGame.launch()

            handle.exit() // or handle.close()

            // Assertions (example)
            assertTrue(createCalled)
            assertTrue(disposeCalled)
            // disposeCalled should become true after exit triggers dispose
        }
}
