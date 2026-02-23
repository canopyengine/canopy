package io.canopy.engine.data.saving

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import com.badlogic.gdx.files.FileHandle
import io.canopy.engine.core.managers.ManagersRegistry
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll

class SaveManagerTests {
    companion object {
        val saveManager =
            SaveManager(
                "player" to { slot ->
                    val file = File("src/test/output/test-$slot.json")
                    FileHandle(file)
                }
            )

        @JvmStatic
        @BeforeAll
        fun setup() {
            for (i in 0..1) {
                val file = FileHandle(File("src/test/output/test-$i.json"))
                if (file.exists()) file.delete()
            }
            ManagersRegistry.register(saveManager)
        }
    }

    @AfterEach
    fun cleanup() {
        ManagersRegistry.get(SaveManager::class).cleanModules("player")
    }

    @Test
    fun `should write empty file`() {
        // Act
        saveManager.save("player", 0)
        // Assert
        val file = FileHandle(File("src/test/output/test-0.json"))
        assertTrue { !file.exists() }
    }

    @Test
    fun `should write data`() {
        // Setup
        var intData = 0

        registerSaveModule(
            "player",
            id = "test-int",
            serializer = Int.serializer(),
            onSave = { 5 },
            onLoad = { intData = it }
        )

        var stringData = ""
        registerSaveModule(
            "player",
            id = "test-string",
            serializer = String.serializer(),
            onSave = { "abc" },
            onLoad = { stringData = it }
        )
        // Act
        saveManager.save("player", 1)
        // Assert
        saveManager.load("player", 1)
        assertEquals(5, intData)
        assertEquals("abc", stringData)
    }
}
