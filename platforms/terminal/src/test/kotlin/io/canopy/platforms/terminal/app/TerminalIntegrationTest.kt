package io.canopy.platforms.terminal.app

import io.canopy.devtools.app.appTestDriver
import io.canopy.engine.core.nodes.types.empty.EmptyNode
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.flows.events.signal
import io.canopy.engine.core.nodes.behavior
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.InputManager
import io.canopy.engine.input.inputs
import io.canopy.engine.core.managers.manager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class TerminalIntegrationTest {

    @Test
    fun `terminal app with reactive node should process frames`() = runBlocking {
        var updateCount = 0
        var lastDelta = 0f

        val driver = appTestDriver(testTerminalApp {
            onEnter {
                // Map input actions (must be after managers are registered)
                inputs(
                    "move_left" to listOf(InputBind.LEFT, InputBind.A),
                    "move_right" to listOf(InputBind.RIGHT, InputBind.D),
                )

                val root = EmptyNode("root") {
                    behavior(
                        onUpdate = { delta: Float ->
                            updateCount++
                            lastDelta = delta
                            // Access input manager to verify it's wired
                            val input = manager<InputManager>()
                            val axis = input.getAxis("move_left", "move_right")
                        }
                    )
                }.asSceneRoot()


            }
        })

        driver.start()

        // Simulate 10 frames at 60fps
        repeat(10) { driver.frame(1f / 60f) }

        assertEquals(10, updateCount)
        assertEquals(1f / 60f, lastDelta, 0.001f)

        driver.stop()
    }

    @Test
    fun `terminal app with signal should track reactive state`() = runBlocking {
        val hp = signal(100)
        var observedHp = 100

        val driver = appTestDriver(testTerminalApp {
            onEnter {
                val root = EmptyNode("root") {
                    behavior(
                        onUpdate = { _: Float ->
                            observedHp = hp()
                        }
                    )
                }.asSceneRoot()


            }
        })

        driver.start()

        // Initial frame
        driver.frame(1f / 60f)
        assertEquals(100, observedHp)

        // Change signal value
        hp.update { it - 10 }

        // Next frame should pick up new value
        driver.frame(1f / 60f)
        assertEquals(90, observedHp)

        driver.stop()
    }

    @Test
    fun `terminal app should run node lifecycle in correct order`() = runBlocking {
        val lifecycleOrder = mutableListOf<String>()

        val driver = appTestDriver(testTerminalApp {
            onEnter {
                val root = EmptyNode("root") {
                    behavior(
                        onEnterTree = { lifecycleOrder += "root:enterTree" },
                        onReady = { lifecycleOrder += "root:ready" },
                        onExitTree = { lifecycleOrder += "root:exitTree" }
                    )

                    EmptyNode("child") {
                        behavior(
                            onEnterTree = { lifecycleOrder += "child:enterTree" },
                            onReady = { lifecycleOrder += "child:ready" },
                            onExitTree = { lifecycleOrder += "child:exitTree" }
                        )
                    }
                }.asSceneRoot()


            }
        })

        driver.start()
        driver.frame(1f / 60f)

        // Lifecycle order: parent enterTree -> children enterTree -> children ready -> parent ready
        assertEquals(
            listOf(
                "root:enterTree",
                "child:enterTree",
                "child:ready",
                "root:ready"
            ),
            lifecycleOrder
        )

        // Set scene to null to trigger exitTree
        io.canopy.engine.core.managers.manager<io.canopy.engine.core.managers.SceneManager>().currScene = null

        // Exit tree order: children exitTree -> parent exitTree
        assertEquals(
            listOf(
                "child:exitTree",
                "root:exitTree"
            ),
            lifecycleOrder.subList(4, 6)
        )
        driver.stop()
    }
}