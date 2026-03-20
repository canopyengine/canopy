package io.canopy.engine.core.nodes

import io.canopy.engine.input.InputEvent

// ===============================
//       NODE BEHAVIOR BASE
// ===============================

/**
 * Base class for behaviors that can be attached to a [Node].
 *
 * Behaviors allow node logic to be composed modularly without requiring
 * subclassing of the node itself.
 *
 * Typical responsibilities of a behavior:
 * - responding to node lifecycle events
 * - running frame or physics updates
 * - encapsulating reusable gameplay logic
 *
 * Example:
 * ```
 * class RotateBehavior(node: MyNode) : Behavior<MyNode>(node) {
 *     override fun onUpdate(delta: Float) {
 *         node?.rotation += 90f * delta
 *     }
 * }
 * ```
 *
 * @param N Type of the [Node] this behavior operates on.
 * @property node Reference to the node the behavior is attached to.
 *                 May be null if the behavior was created detached.
 */
abstract class Behavior<N : Node<N>>(protected open val node: N? = null) {

    /** Secondary constructor allowing behaviors to be created without a node reference. */
    constructor() : this(null)

    // ===============================
    //        LIFECYCLE METHODS
    // ===============================

    /**
     * Called when the node enters the scene tree.
     *
     * At this point the node has a parent and exists within the tree structure,
     * but children may not yet be fully initialized.
     */
    open fun onEnterTree() = Unit

    /**
     * Called when the node and all of its children have completed initialization.
     *
     * This is typically where behaviors should perform setup that depends on
     * the full subtree being available.
     */
    open fun onReady() = Unit

    /**
     * Called when the node exits the scene tree.
     *
     * Use this to release resources or unregister listeners.
     */
    open fun onExitTree() = Unit

    // ===============================
    //           UPDATES
    // ===============================

    /**
     * Called every frame.
     *
     * Intended for general gameplay logic, animations, and rendering-related updates.
     */
    open fun onUpdate(delta: Float) = Unit

    /**
     * Called on each physics tick.
     *
     * Physics ticks run at a fixed step (defined by the SceneManager).
     * Use this for deterministic physics calculations.
     */
    open fun onPhysicsUpdate(delta: Float) = Unit

    open fun onInput(event: InputEvent) = Unit
}

// ===============================
//     LAMBDA BEHAVIOR HELPERS
// ===============================

/**
 * Convenience DSL for attaching a behavior using lambdas instead of creating
 * a subclass of [Behavior].
 *
 * Example:
 * ```
 * node.behavior<MyNode>(
 *     onEnterTree = { println("Node entered the tree!") },
 *     onUpdate = { delta -> println("Updating: $delta") }
 * )
 * ```
 *
 * @param onEnterTree Called when the node enters the scene tree
 * @param onReady Called after the node and its children are fully initialized
 * @param onExitTree Called when the node exits the scene tree
 * @param onUpdate Called every frame
 * @param onPhysicsUpdate Called on physics tick
 */
fun <N : Node<N>> N.behavior(
    onEnterTree: N.() -> Unit = {},
    onReady: N.() -> Unit = {},
    onExitTree: N.() -> Unit = {},
    onUpdate: N.(delta: Float) -> Unit = {},
    onPhysicsUpdate: N.(delta: Float) -> Unit = {},
    onInput: N.(event: InputEvent) -> Unit = {},
) {
    behavior = createBehavior(onEnterTree, onReady, onExitTree, onUpdate, onPhysicsUpdate, onInput)()
}

/**
 * Attaches a behavior created by a builder function.
 *
 * Example:
 * ```
 * node.attachBehavior { MyCustomBehavior(it) }
 * ```
 */
fun <N : Node<N>> N.attachBehavior(builder: (node: N) -> Behavior<N>) {
    behavior = builder(this)
}

/**
 * DSL operator allowing behavior attachment with `+=`.
 *
 * Example:
 * ```
 * node += { MyBehavior(it) }
 * ```
 */
operator fun <N : Node<N>> N.plusAssign(builder: (node: N) -> Behavior<N>) = attachBehavior(builder)

/**
 * Factory function that creates a behavior implementation backed by lambdas.
 *
 * Internally used by the [behavior] DSL helper.
 */
fun <N : Node<N>> createBehavior(
    onEnterTree: N.() -> Unit = {},
    onReady: N.() -> Unit = {},
    onExitTree: N.() -> Unit = {},
    onUpdate: N.(delta: Float) -> Unit = {},
    onPhysicsUpdate: N.(delta: Float) -> Unit = {},
    onInput: N.(InputEvent) -> Unit = {},
) = { node: N ->
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

        override fun onInput(event: InputEvent) {
            onInput(node, event)
        }
    }
}
