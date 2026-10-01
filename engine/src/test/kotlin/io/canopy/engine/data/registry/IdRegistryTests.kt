package io.canopy.engine.data.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import io.canopy.engine.data.core.registry.IdEntry

class IdRegistryTests {
    private data class TestEntry(override val domain: String, override val name: String) : IdEntry

    @Test
    fun `entries can be loaded and mapped by id`() {
        val registry = IdRegistry<TestEntry>()
        val entries = listOf(TestEntry("game", "player"), TestEntry("game", "enemy"))

        registry.loadRegistry(entries)

        assertEquals(2, registry.nEntries())
        assertEquals(entries, registry.mapIds(listOf("game:player", "game:enemy")))
    }

    @Test
    fun `duplicate ids are rejected`() {
        val registry = IdRegistry<TestEntry>()
        val entry = TestEntry("game", "player")

        assertFailsWith<IllegalArgumentException> {
            registry.addItemsToRegistry(listOf(entry, entry))
        }
    }

    @Test
    fun `missing ids are rejected`() {
        val registry = IdRegistry<TestEntry>()

        assertFailsWith<IllegalArgumentException> {
            registry.mapIds<TestEntry>(listOf("game:missing"))
        }
    }
}
