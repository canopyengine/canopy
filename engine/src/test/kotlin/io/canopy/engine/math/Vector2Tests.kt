package io.canopy.engine.math

import kotlin.test.Test
import kotlin.test.assertEquals

class Vector2Tests {
    @Test
    fun `add and plus increase both coordinates`() {
        val vector = Vector2(2f, 3f)

        assertEquals(Vector2(5f, 7f), vector.add(3f, 4f))
        assertEquals(Vector2(7f, 9f), vector + Vector2(2f, 2f))
    }

    @Test
    fun `normalizing zero vector leaves it unchanged`() {
        assertEquals(Vector2.Zero, Vector2.Zero.copy().nor())
    }
}
