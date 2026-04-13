package io.canopy.devtools.app

import kotlin.test.Test
import kotlin.test.assertEquals
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig

class AppTestDriverTests {

    private class RecordingApp : App<AppConfig>() {
        val calls = mutableListOf<String>()

        override fun defaultConfig(): AppConfig = AppConfig("driver")

        override fun internalLaunch(config: AppConfig, vararg args: String) {
            calls += "launch"
            ready()
        }

        override fun afterReady() {
            calls += "ready"
        }

        override fun beforeUpdate(delta: Float) {
            calls += "update:$delta"
        }

        override fun afterResize(width: Int, height: Int) {
            calls += "resize:$width:$height"
        }

        override fun beforeExit() {
            calls += "exit"
        }
    }

    @Test
    fun `driver delegates lifecycle methods to app`() {
        val app = RecordingApp()
        val driver = appTestDriver(app)

        driver.start()
        driver.frame(0.5f)
        driver.resize(320, 200)
        driver.stop()

        assertEquals(
            listOf("ready", "update:0.5", "resize:320:200", "exit"),
            app.calls
        )
    }

    @Test
    fun `driver launch delegates to app launch`() {
        val app = RecordingApp()
        val driver = appTestDriver(app)

        driver.launch()

        assertEquals(listOf("launch", "ready"), app.calls)
    }
}
