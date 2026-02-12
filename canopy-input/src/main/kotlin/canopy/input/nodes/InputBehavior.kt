package canopy.input.nodes

import anchors.framework.input.InputEvent
import canopy.core.nodes.core.Behavior
import canopy.core.nodes.core.Node

abstract class InputBehavior<N : Node<N>>(
    override val node: N? = null,
) : Behavior<N>(node) {
    // ===============================
    //           INPUT
    // ===============================

    /** Called when an input event occurs on the node */
    open fun onInput(
        event: InputEvent,
        delta: Float = 0F,
    ) = Unit
}

/**
 * Convenience helper to define behaviors via lambdas instead of subclassing [Behavior].
 *
 * Example usage:
 * ```
 * val myBehavior = behavior<MyNode>(
 *     onEnterTree = { println("Node entered tree!") },
 *     onUpdate = { delta -> println("Updating with delta $delta") }
 * )
 * ```
 *
 * @param N Node type
 * @param onEnterTree Lambda called when the node enters the tree
 * @param onReady Lambda called when the node and children are ready
 * @param onExitTree Lambda called when the node exits the tree
 * @param onUpdate Lambda called every frame
 * @param onPhysicsUpdate Lambda called on physics tick
 * @return Lambda that creates a [Behavior] instance for a node
 */
fun <N : Node<N>> inputBehavior(
    onEnterTree: N.() -> Unit = {},
    onReady: N.() -> Unit = {},
    onExitTree: N.() -> Unit = {},
    onUpdate: N.(delta: Float) -> Unit = {},
    onPhysicsUpdate: N.(delta: Float) -> Unit = {},
    onInput: N.(event: InputEvent, delta: Float) -> Unit = { _, _ -> },
): (node: N) -> InputBehavior<N> =
    { node ->
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

            override fun onInput(
                event: InputEvent,
                delta: Float,
            ) {
                onInput(node, event, delta)
            }
        }
    }
