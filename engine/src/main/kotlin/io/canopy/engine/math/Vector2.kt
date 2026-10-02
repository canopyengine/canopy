package io.canopy.engine.math

/**
 * Immutable two-dimensional vector.
 *
 * Components cannot be changed after construction. Arithmetic helpers and
 * operators return vector values, so a [Vector2] can be safely shared or used
 * as a map key.
 *
 * Example:
 * ```
 * val position = Vector2(2f, 3f)
 * val nextPosition = position + Vector2(1f, 0f)
 * ```
 */
data class Vector2(
    /** Horizontal component. */
    val x: Float = 0f,
    /** Vertical component. */
    val y: Float = 0f,
) {
    /** Returns a new vector offset by [x] and [y]. */
    fun add(x: Float, y: Float): Vector2 = Vector2(this.x + x, this.y + y)

    /** Returns the component-wise sum without changing either operand. */
    operator fun plus(that: Vector2): Vector2 = Vector2(x + that.x, y + that.y)

    /** Returns a new vector with both components multiplied by [scalar]. */
    fun scl(scalar: Float): Vector2 = Vector2(x * scalar, y * scalar)

    /**
     * Returns a new vector with each component multiplied by its corresponding
     * factor.
     */
    fun scl(x: Float, y: Float) = Vector2(this.x * x, this.y * y)

    /** Returns a new vector with both components multiplied by [scalar]. */
    operator fun times(scalar: Float) = Vector2(x * scalar, y * scalar)

    /**
     * Returns a new vector with each component multiplied by the corresponding
     * component of [scalar].
     */
    operator fun times(scalar: Vector2) = Vector2(x * scalar.x, y * scalar.y)

    /** Returns this vector's Euclidean length. */
    fun len(): Float = kotlin.math.sqrt(x * x + y * y)

    /** Returns a normalized vector, or this vector when its length is zero. */
    fun nor(): Vector2 {
        val length = len()
        return if (length == 0f) this else Vector2(x / length, y / length)
    }

    companion object {
        /** Shared immutable zero vector. */
        val Zero = Vector2(0f, 0f)
    }
}
