package io.canopy.engine.data.saving

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
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
        manager<SaveManager>().cleanModules("other")
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
    fun `loadData reports when a module has not loaded the requested data`() {
        registerSaveModule(
            destination = "player",
            id = "test-int",
            serializer = Int.serializer(),
            onSave = { 5 },
            onLoad = {}
        )

        val error = assertFailsWith<IllegalStateException> {
            saveManager.loadData("player", Int::class)
        }

        assertEquals("No loaded data of type Int for destination player", error.message)
    }

    @Test
    fun `Unit payload is unavailable until actually loaded`() {
        registerSaveModule("player", "unit", Unit.serializer(), onSave = { Unit })
        assertFailsWith<IllegalStateException> { saveManager.loadData("player", Unit::class) }
        saveManager.save("player", 0)
        assertFailsWith<IllegalStateException> { saveManager.loadData("player", Unit::class) }
        saveManager.load("player", 0)
        assertSame(Unit, saveManager.loadData("player", Unit::class))
    }

    @Test
    fun `callback failure retains decoded data and stops later modules`() {
        val failure = IllegalArgumentException("load callback")
        var laterCalls = 0
        registerSaveModule("player", "first", Int.serializer(), onSave = { 5 }, onLoad = { throw failure })
        registerSaveModule("player", "later", String.serializer(), onSave = { "later" }, onLoad = { laterCalls++ })
        saveManager.save("player", 0)
        assertSame(failure, assertFailsWith<IllegalArgumentException> { saveManager.load("player", 0) })
        assertEquals(5, saveManager.loadData("player", Int::class))
        assertFailsWith<IllegalStateException> { saveManager.loadData("player", String::class) }
        assertFailsWith<IllegalStateException> { saveManager.loadData("player", Number::class) }
        assertEquals(0, laterCalls)
    }

    @Test
    fun `save callback failure leaves previous file untouched`() {
        var fail = false
        val failure = IllegalStateException("save callback")
        registerSaveModule("player", "first", Int.serializer(), onSave = { 5 })
        registerSaveModule("player", "later", String.serializer(), onSave = {
            if (fail) throw failure
            "previous"
        })
        saveManager.save("player", 0)
        val previous = entryForSlot(0).readText()
        fail = true
        assertSame(failure, assertFailsWith<IllegalStateException> { saveManager.save("player", 0) })
        assertEquals(previous, entryForSlot(0).readText())
    }

    @Test
    fun `duplicate IDs reject different modules without replacing the registered payload`() {
        registerSaveModule("player", "same", Int.serializer(), onSave = { 5 })
        assertFailsWith<IllegalArgumentException> {
            registerSaveModule("player", "same", String.serializer(), onSave = { "replacement" })
        }
        registerSaveModule("other", "same", String.serializer(), onSave = { "other destination" })
        saveManager.save("player", 0)
        saveManager.load("player", 0)
        assertEquals(5, saveManager.loadData("player", Int::class))
        assertFailsWith<IllegalStateException> { saveManager.loadData("player", String::class) }
    }

    @Test
    fun `equal module registration resets loaded data and retains original callbacks`() {
        fun module(value: Int) = object : SaveModule<Int> {
            override val id = "equal"
            override val serializer = Int.serializer()
            override val onSave = { value }
            override val onLoad: (Int) -> Unit = {}
            override fun equals(other: Any?) = other is SaveModule<*> && other.id == id
            override fun hashCode() = id.hashCode()
        }
        val first = module(7)
        saveManager.registerSaveModule("player", first)
        saveManager.save("player", 0)
        saveManager.load("player", 0)
        assertEquals(7, saveManager.loadData("player", Int::class))
        saveManager.registerSaveModule("player", first)
        assertFailsWith<IllegalStateException> { saveManager.loadData("player", Int::class) }
        saveManager.load("player", 0)
        saveManager.registerSaveModule("player", module(99))
        assertFailsWith<IllegalStateException> { saveManager.loadData("player", Int::class) }
        saveManager.save("player", 0)
        saveManager.load("player", 0)
        assertEquals(7, saveManager.loadData("player", Int::class))
    }

    @Test
    fun `missing files and absent module keys retain the last loaded payload`() {
        var exists = true
        val storage = InMemoryAssetEntry("cached.json")
        val file = object : WritableAssetEntry by storage {
            override fun exists() = exists
        }
        val local = SaveManager("cached" to { file })
        local.registerSaveModule(
            "cached",
            object : SaveModule<Int> {
                override val id = "value"
                override val serializer = Int.serializer()
                override val onSave = { 42 }
                override val onLoad: (Int) -> Unit = {}
            }
        )
        local.save("cached", 0)
        local.load("cached", 0)
        exists = false
        local.load("cached", 1)
        assertEquals(42, local.loadData("cached", Int::class))
        exists = true
        storage.writeText("{}")
        local.load("cached", 2)
        assertEquals(42, local.loadData("cached", Int::class))
    }
}
