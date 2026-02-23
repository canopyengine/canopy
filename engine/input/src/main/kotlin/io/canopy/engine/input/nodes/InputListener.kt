package io.canopy.engine.input.nodes

import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.input.InputEvent

abstract class InputListener<N : Node<N>>(
    private val children: Map<String, *>,
    private val script: InputBehavior<N>?,
) {
    open fun nodeInput(event: InputEvent, delta: Float = 0F) {
        if (event.isHandled) return
        script?.onInput(event, delta)
        if (event.isHandled) return
        // Propagate ito children
        children.values
            .filterIsInstance<InputListener<N>>()
            .forEach { it.nodeInput(event, delta) }
    }
}
