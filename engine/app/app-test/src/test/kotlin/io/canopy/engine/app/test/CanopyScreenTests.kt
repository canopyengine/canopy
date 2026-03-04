package io.canopy.engine.app.test

import kotlin.test.Test
import kotlin.test.assertTrue
import java.util.concurrent.TimeUnit
import io.canopy.engine.app.core.screen.CanopyScreen

class CanopyScreenTests {

    @Test
    fun `screen setup should run when screen starts`() {
        var screenWasCreated = false

        val screen = object : CanopyScreen() {
            override fun setup() {
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
            handle.awaitStarted(1, TimeUnit.SECONDS),
            "App failed to start in time"
        )

        // Now setup() should have run
        assertTrue(screenWasCreated)

        handle.requestExit()
        handle.join()
    }
}
