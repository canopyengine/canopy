package io.canopy.engine.input

import io.canopy.engine.core.managers.SceneManager
import io.canopy.engine.core.managers.lazyManager
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.nodes.TreeSystem
import io.canopy.engine.input.events.ButtonInputEvent
import io.canopy.engine.input.events.InputState
import io.canopy.tooling.utils.UnstableApi

/** Dispatches raw events once per frame before mapped action states, during the FramePre phase. */
@UnstableApi
class InputSystem : TreeSystem(UpdatePhase.FramePre, 10, Node::class) {

    private val input by lazyManager<InputManager>()
    private val scenes by lazyManager<SceneManager>()

    override fun afterProcess(delta: Float) {
        val sceneRoot = scenes.currScene ?: return

        // Deliver raw events first so text typed in the same frame as Enter is available
        // to action handlers before they submit the command.
        input.consumeEventsThisFrame().forEach { event ->
            sceneRoot.nodeInput(event)
        }

        // Dispatch named action states (e.g. ButtonInputEvent for "jump", "move_left", etc.)
        input.actionStates.forEach { (action, state) ->
            when (state) {
                InputState.JustPressed, InputState.Pressed,
                InputState.JustReleased, InputState.Released,
                -> sceneRoot.nodeInput(ButtonInputEvent(action, state))
                else -> Unit
            }
        }
    }
}
