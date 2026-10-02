package io.canopy.engine.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Vector2Tests {
    @Test
    fun `add and plus return new vectors`() {
        val vector = Vector2(2f, 3f)
        val other = Vector2(3f, 4f)

        val added = vector.add(3f, 4f)
        val result = vector + other

        assertEquals(Vector2(5f, 7f), added)
        assertEquals(Vector2(5f, 7f), result)
        assertEquals(Vector2(2f, 3f), vector)
        assertEquals(Vector2(3f, 4f), other)
        assertTrue(vector !== added)
        assertTrue(vector !== result)
    }

    @Test
    fun `multiplication returns new vectors without changing operands`() {
        val vector = Vector2(2f, 3f)
        val other = Vector2(4f, 5f)

        val scalarResult = vector * 2f
        val componentResult = vector * other

        assertEquals(Vector2(4f, 6f), scalarResult)
        assertEquals(Vector2(8f, 15f), componentResult)
        assertEquals(Vector2(2f, 3f), vector)
        assertEquals(Vector2(4f, 5f), other)
        assertTrue(vector !== scalarResult)
        assertTrue(vector !== componentResult)
    }

    @Test
    fun `scaling helpers return new vectors without changing the source`() {
        val vector = Vector2(2f, 3f)

        val uniformResult = vector.scl(2f)
        val componentResult = vector.scl(4f, 5f)

        assertEquals(Vector2(4f, 6f), uniformResult)
        assertEquals(Vector2(8f, 15f), componentResult)
        assertEquals(Vector2(2f, 3f), vector)
        assertTrue(vector !== uniformResult)
        assertTrue(vector !== componentResult)
    }

    @Test
    fun `zero is a shared immutable zero vector`() {
        assertEquals(Vector2(0f, 0f), Vector2.Zero)
        assertTrue(Vector2.Zero === Vector2.Zero)
    }

    @Test
    fun `normalization returns a normalized value without changing the source`() {
        val vector = Vector2(3f, 4f)
        val normalized = vector.nor()

        assertEquals(Vector2(0.6f, 0.8f), normalized)
        assertEquals(Vector2(3f, 4f), vector)
        assertTrue(vector !== normalized)
        assertEquals(Vector2.Zero, Vector2.Zero.nor())
    }
}
