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

        try {
            assertTrue(handle.awaitStarted(10.seconds), "App did not start")
            assertTrue(screenWasCreated)
            handle.requestExit()
            assertTrue(handle.join(5.seconds), "App did not stop gracefully")
        } finally {
            handle.forceClose()
            assertTrue(handle.join(5.seconds), "App cleanup did not complete")
        }
    }
}
