package io.canopy.devtools.app

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.util.concurrent.TimeUnit
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.binds.InputBind

class AppLifecycleTests {

    private class TestInputManager : InputManager() {
        override fun pollPressed(bind: InputBind): Boolean = false
    }

    private open class RecordingApp(
        private val withInputManager: Boolean = false,
        private val failOnLaunch: Boolean = false,
    ) : App<AppConfig>() {
        val calls = mutableListOf<String>()
        val inputManager = TestInputManager()
        var exitRequests = 0
        var forceRequests = 0

        override fun defaultConfig(): AppConfig = AppConfig(title = "test")

        override fun collectManagers(): List<Manager> = if (withInputManager) listOf(inputManager) else emptyList()

        override fun afterReady() {
            calls += "afterReady"
        }

        override fun beforeUpdate(delta: Float) {
            calls += "beforeUpdate:$delta"
        }

        override fun afterResize(width: Int, height: Int) {
            calls += "afterResize:$width:$height"
        }

        override fun beforeExit() {
            calls += "beforeExit"
        }

        override fun internalLaunch(config: AppConfig, vararg args: String) {
            if (failOnLaunch) error("boom")
            installBackendHandle(
                requestExit = { exitRequests++ },
                forceClose = { forceRequests++ }
            )
            ready()
        }
    }

    @AfterTest
    fun cleanup() {
        ManagersRegistry.teardown()
    }

    @Test
    fun `app lifecycle hooks run in expected order`() {
        val app = RecordingApp()
        app.onReady {
            app.calls += "onReady"
        }
        app.onUpdate { delta ->
            app.calls += "onUpdate:$delta"
        }
        app.onResize { width, height ->
            app.calls += "onResize:$width:$height"
        }
        app.onExit {
            app.calls += "onExit"
        }

        app.ready()
        app.update(0.25f)
        app.resize(320, 240)
        app.exit()

        assertEquals(
            listOf(
                "onReady",
                "afterReady",
                "beforeUpdate:0.25",
                "onUpdate:0.25",
                "onResize:320:240",
                "afterResize:320:240",
                "beforeExit",
                "onExit"
            ),
            app.calls
        )
    }

    @Test
    fun `inputs mapping is applied when input manager is registered`() {
        val app = RecordingApp(withInputManager = true)
        app.inputs(
            "jump" to listOf(InputBind.SPACE),
            "left" to listOf(InputBind.A, InputBind.LEFT)
        )

        app.ready()

        assertEquals(setOf("jump", "left"), app.inputManager.actionStates.keys)
        assertEquals(listOf(InputBind.SPACE), app.inputManager.loadMappings()["jump"])
    }

    @Test
    fun `handle routes graceful and force close through backend callbacks`() {
        val app = RecordingApp()

        app.launchAsync().use { handle ->
            assertTrue(handle.awaitStarted(1, TimeUnit.SECONDS))
            handle.requestExit()
            handle.forceClose()
        }

        assertEquals(2, app.exitRequests)
        assertEquals(1, app.forceRequests)
    }

    @Test
    fun `launchAsync surfaces internal launch failures`() {
        val app = RecordingApp(failOnLaunch = true)

        assertFailsWith<IllegalStateException> {
            app.launchAsync()
        }
    }

    private fun TestInputManager.loadMappings(): Map<String, List<InputBind>> {
        val mapper = javaClass.superclass.getDeclaredField("mapper")
        mapper.isAccessible = true
        val inputMapper = mapper.get(this) as io.canopy.engine.input.InputMapper
        return inputMapper.mappings
    }
}
