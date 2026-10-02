package io.canopy.engine.data.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import io.canopy.engine.data.assets.AssetEntry
import io.canopy.engine.data.core.registry.IdEntry
import io.canopy.engine.data.saving.InMemoryAssetEntry
import kotlinx.serialization.Serializable

class IdRegistryTests {
    private interface TestIdEntry : IdEntry

    @Serializable
    private data class TestEntry(
        override val domain: String,
        override val name: String,
        var updated: Boolean = false,
    ) : TestIdEntry

    @Serializable
    private data class OtherEntry(override val domain: String, override val name: String) : TestIdEntry

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

    @Test
    fun `registry loads nested json files and ignores other file types`() {
        val root = InMemoryAssetEntry("registry", isDirectory = true)
        val nested = InMemoryAssetEntry("registry/nested", isDirectory = true)
        nested.addChild(
            InMemoryAssetEntry(
                "registry/nested/player.json",
                """[{"domain":"game","name":"player"}]"""
            )
        )
        nested.addChild(InMemoryAssetEntry("registry/nested/readme.txt", "ignored"))
        root.addChild(nested)

        val registry = IdRegistry<TestEntry>(root)
        registry.loadRegistry<TestEntry>()

        assertEquals(listOf("game:player"), registry.map.keys.toList())
    }

    @Test
    fun `registry loads a single json file`() {
        val source = InMemoryAssetEntry(
            "registry.json",
            """[{"domain":"game","name":"player"}]"""
        )

        val registry = IdRegistry<TestEntry>(source)
        registry.loadRegistry<TestEntry>()

        assertEquals(listOf("game:player"), registry.map.keys.toList())
    }

    @Test
    fun `registry rejects a source that does not exist`() {
        val source = object : AssetEntry by InMemoryAssetEntry("missing") {
            override fun exists() = false
        }

        assertFailsWith<IllegalStateException> { IdRegistry<TestEntry>(source) }
    }

    @Test
    fun `registry rejects loading from a missing source`() {
        val registry = IdRegistry<TestEntry>()

        assertFailsWith<IllegalStateException> { registry.loadRegistry<TestEntry>() }
    }

    @Test
    fun `empty id input returns empty and update handler runs on resolved entries`() {
        val registry = IdRegistry<TestEntry>()
        val entry = TestEntry("game", "player")
        registry.loadRegistry(listOf(entry))

        assertTrue(registry.mapIds<TestEntry>(emptyList()).isEmpty())
        val updated = registry.mapIds<TestEntry>(listOf(entry.id)) { updated = true }

        assertTrue(updated.single().updated)
    }

    @Test
    fun `mapping an id to the wrong subtype is rejected`() {
        val registry = IdRegistry<TestIdEntry>()
        val entry = OtherEntry("game", "enemy")
        registry.loadRegistry<TestIdEntry>(listOf(entry))

        assertFailsWith<IllegalArgumentException> {
            registry.mapIds<TestEntry>(listOf(entry.id))
        }
    }
}
