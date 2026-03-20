package io.canopy.engine.input

import io.canopy.engine.core.managers.Manager
import io.canopy.engine.core.managers.lazyManager
import io.canopy.engine.core.nodes.TreeSystem
import io.canopy.tooling.utils.UnstableApi

@UnstableApi
class InputSystem :
    TreeSystem(UpdatePhase.PhysicsPre, 10),
    Manager {

    private val input by lazyManager<InputManager>()

    override fun afterProcess(delta: Float) {
        input.updateActions()

        input.mapper.actions.keys.forEach { action ->
            when (input.getActionState(action)) {
                InputState.JustPressed -> dispatch(action, InputState.JustPressed, delta)
                InputState.Pressed -> dispatch(action, InputState.Pressed, delta)
                InputState.JustReleased -> dispatch(action, InputState.JustReleased, delta)
                InputState.Released -> Unit
                else -> Unit
            }
        }
    }

    private fun dispatch(action: String, state: InputState, delta: Float) {
        // TODO: propagate to scene tree
    }
}
