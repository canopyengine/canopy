package io.canopy.engine.data.saving

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.manager
import io.canopy.engine.data.assets.WritableAssetEntry
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll

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

        @JvmStatic
        @BeforeAll
        fun setup() {
            entries.clear()
            ManagersRegistry.register(saveManager)
        }
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
}
