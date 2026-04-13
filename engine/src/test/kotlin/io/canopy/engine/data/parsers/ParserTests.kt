package io.canopy.engine.data.parsers

import kotlin.test.Test
import kotlin.test.assertEquals
import io.canopy.engine.data.saving.InMemoryAssetEntry
import kotlinx.serialization.Serializable

class ParserTests {

    @Serializable
    data class TestData(val name: String, val value: Int)

    @Test
    fun `Json toFile and fromFile works`() {
        val asset = InMemoryAssetEntry("test.json")
        val data = TestData("json", 10)

        Json.toFile(data, asset)
        val loaded: TestData = Json.fromFile(asset)

        assertEquals("json", loaded.name)
        assertEquals(10, loaded.value)
    }

    @Test
    fun `Toml toFile and fromFile works`() {
        val asset = InMemoryAssetEntry("test.toml")
        val data = TestData("toml", 20)

        Toml.toFile(data, asset)
        val loaded: TestData = Toml.fromFile(asset)

        assertEquals("toml", loaded.name)
        assertEquals(20, loaded.value)
    }
}
