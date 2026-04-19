package io.canopy.engine.input

import io.canopy.engine.core.managers.lazyManager
import io.canopy.engine.core.nodes.TreeSystem
import io.canopy.tooling.utils.UnstableApi

@UnstableApi
class InputSystem : TreeSystem(UpdatePhase.PhysicsPre, 10) {

    private val input by lazyManager<InputManager>()

    override fun afterProcess(delta: Float) {
        input.updateActions()

        input.actionStates.forEach { (action, state) ->
            when (state) {
                InputState.JustPressed -> dispatch(action, InputState.JustPressed)
                InputState.Pressed -> dispatch(action, InputState.Pressed)
                InputState.JustReleased -> dispatch(action, InputState.JustReleased)
                InputState.Released -> Unit
                else -> Unit
            }
        }
    }

    private fun dispatch(action: String, state: InputState) {
        val inputEvent = ButtonInputEvent(action, state)

        matchingNodes.forEach { node ->
            node.nodeInput(inputEvent)
        }
    }
}
