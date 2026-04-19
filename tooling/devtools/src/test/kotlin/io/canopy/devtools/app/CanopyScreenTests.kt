package io.canopy.devtools.app

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import io.canopy.engine.app.Screen
import io.canopy.engine.app.screens
import kotlinx.coroutines.runBlocking

class CanopyScreenTests {

    @Test
    fun `screen setup should run when screen starts`() = runBlocking {
        var screenWasCreated = false

        val screen = object : Screen() {
            override fun onEnter() {
                screenWasCreated = true
            }
        }

        val handle = testHeadlessApp {
            screens {
                start(screen)
            }
        }.launchAsync()

        // Wait until the app finished booting
        assertTrue(
            handle.awaitStarted(2.seconds),
            "App failed to start in time"
        )

        // Now setup() should have run
        assertTrue(screenWasCreated)

        handle.requestExit()
        handle.join()
    }
}
