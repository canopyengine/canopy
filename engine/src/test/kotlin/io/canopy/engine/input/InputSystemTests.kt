package io.canopy.engine.input

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.input.binds.InputBind
import io.canopy.tooling.utils.UnstableApi

@OptIn(UnstableApi::class)
class InputSystemTests {

    private class TestInputManager : InputManager() {
        private val pressed = mutableSetOf<InputBind>()

        fun press(vararg binds: InputBind) {
            pressed += binds
        }

        override fun pollPressed(bind: InputBind): Boolean = bind in pressed
    }

    private class RecordingNode(name: String = "node") : Node<RecordingNode>(name) {
        val received = mutableListOf<Pair<String, InputState>>()

        override fun nodeInput(event: InputEvent) {
            received += event.action to event.state
            super.nodeInput(event)
        }
    }

    @AfterTest
    fun cleanup() {
        ManagersRegistry.teardown()
    }

    @Test
    fun `input system dispatches button events to nodes`() {
        val input = TestInputManager()
        val sceneManager = SceneManager {
            +InputSystem()
        }

        ManagersRegistry.withScope {
            register(input)
            register(sceneManager)
        }

        input.mapActions("jump" to listOf(InputBind.SPACE))
        val root = RecordingNode("root")
        sceneManager.currScene = root

        input.press(InputBind.SPACE)
        sceneManager.tick(1f / 60f)
        sceneManager.tick(1f / 60f)

        assertEquals(
            listOf("jump" to InputState.JustPressed, "jump" to InputState.Pressed),
            root.received
        )
    }
}
