package io.canopy.engine.utils

import com.badlogic.gdx.math.Vector2

fun <T> T.interpolate(b: T, t: Float): T = when (this) {
    is Float -> (this + (b as Float - this) * t) as T

    is Vector2 ->
        Vector2(
            this.x + ((b as Vector2).x - this.x) * t,
            this.y + ((b as Vector2).y - this.y) * t
        ) as T

    else -> this
}
