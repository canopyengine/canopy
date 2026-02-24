package io.canopy.engine.app.test

import kotlin.test.Test
import kotlin.test.assertTrue
import io.canopy.engine.app.core.screen.CanopyScreen

class CanopyScreenTests {

    @Test
    fun `screen test`() {
        var screenWasCreated = false

        val screen = object : CanopyScreen() {
            override fun setup() {
                screenWasCreated = true
            }
        }

        testHeadlessApp {
            screens {
                start(screen)
            }
        }.launchAsync()

        assertTrue { screenWasCreated }
    }
}
