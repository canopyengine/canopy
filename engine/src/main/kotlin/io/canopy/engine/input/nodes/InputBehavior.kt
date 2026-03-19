package io.canopy.engine.input.nodes

import io.canopy.engine.core.nodes.Behavior
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.input.InputEvent

/**
 * Specialized [Behavior] that can react to input events.
 *
 * This is typically used by nodes that want to receive input events
 * dispatched by the input system (keyboard, mouse, controller, etc.).
 *
 * Extend this class when creating reusable input logic,
 * or use [inputBehavior] for lightweight inline definitions.
 */
abstract class InputBehavior<N : Node<N>>(override val node: N? = null) : Behavior<N>(node) {

    // ===============================
    //           INPUT
    // ===============================

    /**
     * Called when an [InputEvent] occurs on the node.
     *
     * @param event the input event received (keyboard, mouse, etc.)
     * @param delta optional delta time since the last frame (if the input
     *              system propagates it alongside the event)
     */
    open fun onInput(event: InputEvent, delta: Float = 0f) = Unit
}

/**
 * Convenience helper to define [InputBehavior] via lambdas instead of subclassing.
 *
 * This allows quickly attaching input-driven behavior to a node.
 *
 * Example:
 * ```
 * val moveBehavior = inputBehavior<PlayerNode>(
 *     onReady = { println("Player ready!") },
 *     onInput = { event, _ ->
 *         if (event.isPressed("move_left")) {
 *             position.x -= 10f
 *         }
 *     }
 * )
 *
 * playerNode.attachBehavior(moveBehavior)
 * ```
 *
 * @param N Node type the behavior is attached to
 * @param onEnterTree called when the node enters the scene tree
 * @param onReady called once the node and its children are initialized
 * @param onExitTree called when the node exits the scene tree
 * @param onUpdate called every frame
 * @param onPhysicsUpdate called on each physics tick
 * @param onInput called when an input event is dispatched to the node
 *
 * @return a factory function that produces an [InputBehavior] instance bound to a node
 */
fun <N : Node<N>> inputBehavior(
    onEnterTree: N.() -> Unit = {},
    onReady: N.() -> Unit = {},
    onExitTree: N.() -> Unit = {},
    onUpdate: N.(delta: Float) -> Unit = {},
    onPhysicsUpdate: N.(delta: Float) -> Unit = {},
    onInput: N.(event: InputEvent, delta: Float) -> Unit = { _, _ -> },
): (node: N) -> InputBehavior<N> = { node ->
    object : InputBehavior<N>(node) {

        override fun onEnterTree() {
            onEnterTree(node)
        }

        override fun onReady() {
            onReady(node)
        }

        override fun onExitTree() {
            onExitTree(node)
        }

        override fun onUpdate(delta: Float) {
            onUpdate(node, delta)
        }

        override fun onPhysicsUpdate(delta: Float) {
            onPhysicsUpdate(node, delta)
        }

        override fun onInput(event: InputEvent, delta: Float) {
            onInput(node, event, delta)
        }
    }
}
