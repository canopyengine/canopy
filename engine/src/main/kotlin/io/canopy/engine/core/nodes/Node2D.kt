package io.canopy.engine.core.nodes

import io.canopy.engine.math.Vector2

/**
 * Base node for objects with a two-dimensional transform.
 *
 * Each node stores a local [position], [scale], and [rotation]. The
 * [globalPosition], [globalScale], and [globalRotation] properties combine
 * those local values with the corresponding values of 2D parents.
 *
 * [Vector2] values are immutable, so transform components are changed by
 * assigning a new vector:
 * ```
 * position = Vector2(12f, 8f)
 * ```
 *
 * @param N concrete node type, used to preserve the node DSL receiver type.
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
            val p = parent as? Node2D ?: return position.copy()
            return position + p.globalPosition
        }

    /**
     * Scale in world space (local scale + parent global scale).
     */
    val globalScale: Vector2
        get() {
            val p = parent as? Node2D ?: return scale.copy()
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
    open var position: Vector2 = Vector2()

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
