package io.canopy.engine.data.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import io.canopy.engine.data.core.registry.IdEntry
import io.canopy.engine.data.saving.InMemoryAssetEntry
import kotlinx.serialization.Serializable

class IdRegistryTests {

    @Serializable
    private data class TestEntry(
        override val domain: String,
        override val name: String,
        var touched: Boolean = false,
    ) : IdEntry

    @Test
    fun `addItemsToRegistry rejects duplicate ids`() {
        val registry = IdRegistry<TestEntry>()

        assertFailsWith<IllegalArgumentException> {
            registry.addItemsToRegistry(
                listOf(
                    TestEntry("test", "one"),
                    TestEntry("test", "one")
                )
            )
        }
    }

    @Test
    fun `mapIds resolves entries and applies update handler`() {
        val registry = IdRegistry<TestEntry>()
        registry.addItemsToRegistry(listOf(TestEntry("test", "one"), TestEntry("test", "two")))

        val mapped = registry.mapIds<TestEntry>(listOf("test:one", "test:two")) {
            touched = true
        }

        assertEquals(listOf("test:one", "test:two"), mapped.map { it.id })
        assertTrue(mapped.all { it.touched })
    }

    @Test
    fun `mapIds throws for missing ids`() {
        val registry = IdRegistry<TestEntry>()

        assertFailsWith<IllegalArgumentException> {
            registry.mapIds<TestEntry>(listOf("test:missing"))
        }
    }

    @Test
    fun `loadRegistry requires a source when items are not provided`() {
        val registry = IdRegistry<TestEntry>()

        assertFailsWith<IllegalStateException> {
            registry.loadRegistry<TestEntry>()
        }
    }

    @Test
    fun `collectJsonFiles returns nested json files only`() {
        val root = InMemoryAssetEntry("root", isDirectory = true)
        val nested = InMemoryAssetEntry("nested", isDirectory = true)
        val firstJson = InMemoryAssetEntry("root/one.json")
        val secondJson = InMemoryAssetEntry("root/nested/two.json")

        root.addChild(firstJson)
        root.addChild(InMemoryAssetEntry("root/readme.txt"))
        root.addChild(nested)
        nested.addChild(secondJson)

        val registry = IdRegistry<TestEntry>(root)

        assertEquals(listOf(firstJson, secondJson), registry.collectJsonFiles(root))
    }

    @Test
    fun `loadRegistry loads from JSON file`() {
        val root = InMemoryAssetEntry("root/one.json")
        root.writeText("""[{"domain":"test","name":"one"}]""")

        val registry = IdRegistry<TestEntry>(root)
        registry.loadRegistry<TestEntry>()

        assertEquals(1, registry.nEntries())
        assertEquals("test:one", registry.map["test:one"]?.id)
    }
}
