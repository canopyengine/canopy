package io.canopy.engine.data.saving

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.manager
import io.canopy.engine.data.assets.WritableAssetEntry
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Tests for [SaveManager] + [SaveModule] integration.
 *
 * Validates:
 * - save() is a no-op when no modules are registered
 * - registered modules are saved and loaded correctly (roundtrip)
 */
class SaveManagerTests {

    companion object {
        private val entries = mutableMapOf<Int, InMemoryAssetEntry>()

        private fun entryForSlot(slot: Int): WritableAssetEntry =
            entries.getOrPut(slot) { InMemoryAssetEntry("player-$slot.json") }

        val saveManager = SaveManager(
            "player" to ::entryForSlot
        )

        fun reset() {
            entries.clear()
            ManagersRegistry.teardown()
            ManagersRegistry.register(saveManager)
        }
    }

    @BeforeEach
    fun setup() {
        reset()
    }

    @AfterEach
    fun cleanup() {
        manager<SaveManager>().cleanModules("player")
        entries.clear()
    }

    @Test
    fun `save should not create a file when no modules are registered`() {
        saveManager.save("player", 0)

        assertFalse(entries.containsKey(0))
    }

    @Test
    fun `save then load should roundtrip module data`() {
        var intData = 0
        registerSaveModule(
            destination = "player",
            id = "test-int",
            serializer = Int.serializer(),
            onSave = { 5 },
            onLoad = { intData = it }
        )

        var stringData = ""
        registerSaveModule(
            destination = "player",
            id = "test-string",
            serializer = String.serializer(),
            onSave = { "abc" },
            onLoad = { stringData = it }
        )

        saveManager.save("player", 1)
        saveManager.load("player", 1)

        assertEquals(5, intData)
        assertEquals("abc", stringData)
    }

    @Test
    fun `load should ignore missing destinations and missing module payloads`() {
        var loaded = 0
        registerSaveModule(
            destination = "player",
            id = "test-int",
            serializer = Int.serializer(),
            onSave = { 7 },
            onLoad = { loaded = it }
        )

        saveManager.load("missing", 3)
        assertEquals(0, loaded)

        entries.getOrPut(3) { InMemoryAssetEntry("player-3.json", """{"other": 99}""") }
        saveManager.load("player", 3)

        assertEquals(0, loaded)
    }

    @Test
    fun `saveAll and loadAll process every destination`() {
        val settingsEntries = mutableMapOf<Int, InMemoryAssetEntry>()
        fun settingsEntryForSlot(slot: Int): WritableAssetEntry =
            settingsEntries.getOrPut(slot) { InMemoryAssetEntry("settings-$slot.json") }

        val manager = SaveManager(
            "player" to ::entryForSlot,
            "settings" to ::settingsEntryForSlot
        )

        ManagersRegistry.teardown()
        ManagersRegistry.register(manager)

        var player = 0
        var settings = ""
        registerSaveModule(
            destination = "player",
            id = "player-score",
            serializer = Int.serializer(),
            onSave = { 42 },
            onLoad = { player = it }
        )
        registerSaveModule(
            destination = "settings",
            id = "difficulty",
            serializer = String.serializer(),
            onSave = { "hard" },
            onLoad = { settings = it }
        )

        manager.saveAll(0)
        manager.loadAll(0)

        assertEquals(42, player)
        assertEquals("hard", settings)
    }

    @Test
    fun `cleanModules removes registered module data`() {
        registerSaveModule(
            destination = "player",
            id = "test-int",
            serializer = Int.serializer(),
            onSave = { 5 }
        )

        saveManager.cleanModules("player")

        assertFailsWith<NoSuchElementException> {
            saveManager.loadData("player", Int::class)
        }
    }
}
