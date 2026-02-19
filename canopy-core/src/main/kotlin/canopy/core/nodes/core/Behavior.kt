package canopy.core.nodes.core

// ===============================
//       NODE BEHAVIOR BASE
// ===============================

/**
 * Represents a custom behavior attached to a [Node].
 *
 * Behaviors allow modular logic to run on nodes without subclassing the node itself.
 *
 * See more [here](https://github.com/canopyengine/canopy-docs/blob/main/docs/manuals/core/node-system.md).
 *
 * @param N Type of the Node this behavior is attached to.
 * @property node Optional reference to the node. Can be null if detached.
 */
abstract class Behavior<N : Node<N>>(protected open val node: N? = null) {
    /** Secondary constructor for convenience */
    constructor() : this(null)

    // ===============================
    //        LIFECYCLE METHODS
    // ===============================

    /** Called when the node enters the tree */
    open fun onEnterTree() = Unit

    /** Called after the node and all its children have been initialized */
    open fun onReady() = Unit

    /** Called when the node exits the tree */
    open fun onExitTree() = Unit

    // ===============================
    //           UPDATES
    // ===============================

    /**
     * Called every frame.
     * Use for rendering or non-physics logic.
     */
    open fun onUpdate(delta: Float) = Unit

    /**
     * Called on each physics tick.
     * Use for deterministic physics calculations.
     */
    open fun onPhysicsUpdate(delta: Float) = Unit
}

// ===============================
//     LAMBDA BEHAVIOR HELPER
// ===============================

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
fun <N : Node<N>> behavior(
    onEnterTree: N.() -> Unit = {},
    onReady: N.() -> Unit = {},
    onExitTree: N.() -> Unit = {},
    onUpdate: N.(delta: Float) -> Unit = {},
    onPhysicsUpdate: N.(delta: Float) -> Unit = {},
): (node: N) -> Behavior<N> = { node ->
    object : Behavior<N>(node) {
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
    }
}
