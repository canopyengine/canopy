package io.canopy.devtools.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.behavior
import io.canopy.engine.core.nodes.types.empty.EmptyNode

/** Direct engine driving deliberately bypasses platform launch and native terminal services. */
class AppTestDriverTests {
    private class ManualApp : App<AppConfig>() {
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) = error("Driver must bypass native launch")
    }

    @Test
    fun `driver brackets lifecycle and advances frame count only while running`() {
        val calls = mutableListOf<String>()
        val app = ManualApp().apply {
            onEnter { calls += "enter" }
            onUpdate { calls += "frame:$it" }
            onExit { calls += "exit" }
        }
        val driver = appTestDriver(app)
        try {
            driver.start()
            driver.frame(0.1f)
            app.pause()
            driver.frame(0.1f)
            app.resume()
            driver.frame(0.1f)
        } finally {
            driver.stop()
        }

        assertEquals(listOf("enter", "frame:0.1", "frame:0.0", "frame:0.1", "exit"), calls)
        assertEquals(2L, app.frameCount)
        assertNull(ManagersRegistry.getManagerOrNull(SceneManager::class))
    }

    @Test
    fun `driver dispatches fixed physics before frame callbacks and brackets the scene visit`() {
        val calls = mutableListOf<String>()
        val app = ManualApp().apply {
            onPhysicsUpdate { calls += "physics" }
            onUpdate { calls += "frame" }
            onEnter {
                manager<SceneManager>().currScene = EmptyNode("root") {
                    behavior(
                        onEnterTree = { calls += "root:enter" },
                        onReady = { calls += "root:ready" },
                        onUpdate = { calls += "node:frame" },
                        onExitTree = { calls += "root:exit" }
                    )
                    EmptyNode("child") {
                        behavior(
                            onEnterTree = { calls += "child:enter" },
                            onReady = { calls += "child:ready" },
                            onExitTree = { calls += "child:exit" }
                        )
                    }
                }
            }
        }
        val driver = appTestDriver(app)
        try {
            driver.start()
            driver.frame(app.engineLoop.physicsStep * 2)
        } finally {
            driver.stop()
        }

        assertEquals(
            listOf(
                "root:enter", "child:enter", "child:ready", "root:ready",
                "physics", "physics", "frame", "node:frame", "child:exit", "root:exit"
            ),
            calls
        )
    }

    @Test
    fun `driver forwards geometry to app callbacks and scene state`() {
        val sizes = mutableListOf<Pair<Int, Int>>()
        val app = ManualApp().apply { onResize { width, height -> sizes += width to height } }
        val driver = appTestDriver(app)
        try {
            driver.start()
            driver.resize(120, 40)
            assertEquals(listOf(120 to 40), sizes)
            val size = manager<SceneManager>().sceneSize()
            assertEquals(120f, size.x)
            assertEquals(40f, size.y)
        } finally {
            driver.stop()
        }
    }
}
