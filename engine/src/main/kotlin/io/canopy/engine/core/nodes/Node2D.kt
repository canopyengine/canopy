package io.canopy.engine.core.nodes

import io.canopy.engine.math.Vector2

/**
 * Base 2D Node
 */
abstract class Node2D<N : Node2D<N>> protected constructor(name: String, block: N.() -> Unit = {}) :
    Node<N>(name, block = block) {

    /* ============================================================
     * Global transform helpers
     * ============================================================ */

    /**
     * Position in world space (local position + parent global position).
     */
    val globalPosition: Vector2
        get() {
            val p = parent as? Node2D ?: return position
            return position + p.globalPosition
        }

    /**
     * Scale in world space (local scale + parent global scale).
     */
    val globalScale: Vector2
        get() {
            val p = parent as? Node2D ?: return scale
            return scale * p.globalScale
        }

    /**
     * Rotation in world space (local rotation + parent global rotation).
     */
    val globalRotation: Float
        get() {
            val p = parent as? Node2D ?: return rotation
            return rotation + p.globalRotation
        }

    /* ============================================================
     * Local transform
     * ============================================================ */

    /**
     * Local position in 2D space.
     */
    open var position: Vector2 = Vector2.Zero

    /**
     * Local scale in 2D space.
     */
    var scale: Vector2 = Vector2(1f, 1f)

    /**
     * Local rotation in radians.
     */
    open var rotation: Float = 0f

    /* ============================================================
     * DSL helpers
     * ============================================================ */
}
