package io.canopy.engine.input.nodes

import io.canopy.engine.core.nodes.core.Node
import io.canopy.engine.input.InputEvent

/**
 * Base helper for propagating input events through a node tree.
 *
 * Propagation model:
 * 1) If the event is already handled, stop immediately.
 * 2) Give the current node's [script] a chance to handle it.
 * 3) If still not handled, propagate the event to child listeners.
 *
 * This mirrors common UI/game input bubbling:
 * - "handled" short-circuits the rest of the propagation chain
 * - children only receive the event if the parent didn't consume it
 *
 * Notes:
 * - [children] is intentionally typed as `Map<String, *>` so this can wrap different
 *   node implementations without requiring a strict child type.
 * - Only children that are also [InputListener] participate in propagation.
 */
abstract class InputListener<N : Node<N>>(
    private val children: Map<String, *>,
    private val script: InputBehavior<N>?,
) {

    /**
     * Entry point for input propagation.
     *
     * @param event input event (may be marked handled by any listener)
     * @param delta optional delta time forwarded by the input system
     */
    open fun nodeInput(event: InputEvent, delta: Float = 0f) {
        // If someone already consumed the event, don't propagate further.
        if (event.isHandled) return

        // Let this node handle input first.
        script?.onInput(event, delta)

        // If handled by this node, stop propagation.
        if (event.isHandled) return

        // Propagate to children (depth-first), but only to children that also implement InputListener.
        children.values
            .filterIsInstance<InputListener<N>>()
            .forEach { it.nodeInput(event, delta) }
    }
}
