package io.canopy.engine.data.saving

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import com.badlogic.gdx.files.FileHandle
import io.canopy.engine.core.managers.ManagersRegistry
import io.canopy.engine.core.managers.manager
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll

/**
 * Tests for [SaveManager] + [SaveModule] integration.
 *
 * These tests validate:
 * - save() is a no-op when no modules are registered for a destination
 * - registered modules are saved and loaded correctly (roundtrip)
 */
class SaveManagerTests {

    companion object {
        private val outputDir = File("src/test/output")

        // Destination "player" writes to src/test/output/test-<slot>.json
        val saveManager =
            SaveManager(
                "player" to { slot ->
                    val file = File(outputDir, "test-$slot.json")
                    FileHandle(file)
                }
            )

        @JvmStatic
        @BeforeAll
        fun setup() {
            // Ensure a clean test directory / files.
            outputDir.mkdirs()

            for (i in 0..1) {
                val file = FileHandle(File(outputDir, "test-$i.json"))
                if (file.exists()) file.delete()
            }

            // Register SaveManager globally so registerSaveModule(...) can find it.
            ManagersRegistry.register(saveManager)
        }
    }

    @AfterEach
    fun cleanup() {
        // Important: tests share the same SaveManager instance.
        // Clear modules after each test to prevent cross-test interference.
        manager<SaveManager>().cleanModules("player")
    }

    @Test
    fun `save should not create a file when no modules are registered`() {
        // With no registered modules for "player", save() should be a no-op.
        saveManager.save("player", 0)

        val file = FileHandle(File(outputDir, "test-0.json"))
        assertTrue { !file.exists() }
    }

    @Test
    fun `save then load should roundtrip module data`() {
        // This test registers two modules, saves them, then loads them back
        // and verifies each module's onLoad receives the decoded value.

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

        // Act: write to slot 1
        saveManager.save("player", 1)

        // Act: read from slot 1 (should invoke onLoad for each registered module)
        saveManager.load("player", 1)

        // Assert: onLoad callbacks received the persisted values
        assertEquals(5, intData)
        assertEquals("abc", stringData)
    }
}
