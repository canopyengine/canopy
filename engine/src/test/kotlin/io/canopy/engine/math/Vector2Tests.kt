package io.canopy.engine.math

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class Vector2Tests {

    @Test
    fun `set add and scalar scale mutate and return same instance`() {
        val vector = Vector2(1f, 2f)

        assertSame(vector, vector.set(3f, 4f))
        assertSame(vector, vector.add(2f, -1f))
        assertSame(vector, vector.scl(2f))
        assertEquals(Vector2(10f, 6f), vector)
    }

    @Test
    fun `plus and vector scale return scaled values`() {
        val vector = Vector2(2f, 3f)
        val multiplied = Vector2(2f, 3f)

        assertEquals(Vector2(3f, 4f), vector + Vector2(1f, 1f))
        assertEquals(Vector2(4f, 9f), multiplied * Vector2(2f, 3f))
    }

    @Test
    fun `len and nor compute vector magnitude and normalization`() {
        val vector = Vector2(3f, 4f)

        assertEquals(5f, vector.len())

        vector.nor()

        assertEquals(1f, vector.len(), 0.0001f)
        assertEquals(3f / sqrt(25f), vector.x, 0.0001f)
        assertEquals(4f / sqrt(25f), vector.y, 0.0001f)
    }

    @Test
    fun `normalizing zero vector keeps zero`() {
        val zero = Vector2.Zero.copy()

        zero.nor()

        assertEquals(Vector2(0f, 0f), zero)
    }
}
