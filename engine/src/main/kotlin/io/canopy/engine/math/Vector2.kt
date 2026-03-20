package io.canopy.engine.math

data class Vector2(var x: Float = 0f, var y: Float = 0f) {
    fun set(x: Float, y: Float): Vector2 {
        this.x = x
        this.y = y
        return this
    }

    fun add(x: Float, y: Float): Vector2 {
        this.x += x
        this.y += y
        return this
    }

    operator fun plus(that: Vector2): Vector2 = this.add(that.x, that.y)

    fun scl(scalar: Float): Vector2 {
        this.x *= scalar
        this.y *= scalar
        return this
    }

    fun scl(x: Float, y: Float) = Vector2(this.x * x, this.y * y)

    operator fun times(scalar: Float) = this.scl(scalar)
    operator fun times(scalar: Vector2) = this.scl(scalar.x, scalar.y)

    fun len(): Float = kotlin.math.sqrt(x * x + y * y)

    fun nor(): Vector2 {
        val length = len()
        if (length != 0f) {
            x /= length
            y /= length
        }
        return this
    }

    companion object {
        val Zero = Vector2(0f, 0f)
    }
}
