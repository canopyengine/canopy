package io.canopy.engine.input

import kotlin.test.Test
import kotlin.test.assertEquals
import io.canopy.engine.app.App
import io.canopy.engine.app.AppConfig
import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.manager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.input.binds.InputBind
import io.canopy.engine.input.events.ButtonInputEvent
import io.canopy.engine.input.events.InputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.engine.input.events.TextInputEvent

@OptIn(io.canopy.tooling.utils.UnstableApi::class)
class InputSystemTests {
    private class Receiver : Node<Receiver>("receiver") {
        var received by nodeProperty<List<InputEvent>>(emptyList())
        override fun onInput(event: InputEvent) {
            received = received + event
        }
    }
    private class TestInput : InputManager() {
        var pressed = false
        override fun pollPressed(bind: InputBind) = pressed && bind == InputBind.SPACE
    }
    private class TestApp(private val input: TestInput, private val system: InputSystem) : App<AppConfig>() {
        override fun defaultConfig() = AppConfig()
        override fun internalLaunch(config: AppConfig, vararg args: String) = Unit
        override fun provideManagers() = listOf(input)
        override fun SceneManager.configureSceneManager() {
            addSystem(system)
        }
        override fun processFrameInput() {
            input.processEvents()
        }
    }

    @Test
    fun `reused input system delivers text and current action states across successive apps`() {
        val system = InputSystem()
        repeat(2) { run ->
            // Arrange
            val input = TestInput()
            input.mapActions("jump-$run" to listOf(InputBind.SPACE))
            val app = TestApp(input, system)
            try {
                app.enter()
                val receiver = Receiver()
                manager<SceneManager>().currScene = receiver
                input.enqueue(TextInputEvent("run-$run"))
                input.pressed = true

                // Act
                app.update(0f)

                // Assert
                assertEquals(listOf("run-$run"), receiver.received.filterIsInstance<TextInputEvent>().map { it.text })
                assertEquals(
                    listOf("jump-$run" to InputState.JustPressed),
                    receiver.received.filterIsInstance<ButtonInputEvent>().map { it.action to it.state }
                )
                app.update(0f)
                assertEquals(1, receiver.received.filterIsInstance<TextInputEvent>().size)
                assertEquals(InputState.Pressed, receiver.received.filterIsInstance<ButtonInputEvent>().last().state)
            } finally {
                app.exit()
            }
        }
    }
}
