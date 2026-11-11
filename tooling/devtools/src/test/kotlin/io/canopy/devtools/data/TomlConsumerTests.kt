package io.canopy.devtools.data

import kotlin.test.Test
import kotlin.test.assertEquals
import io.canopy.engine.data.parsers.Toml

/** Compiles against engine API dependencies, without a direct tomlkt dependency. */
class TomlConsumerTests {
    @Test
    fun `engine consumers can configure and use the public TOML codec`() {
        // Arrange
        val values = mapOf("name" to "Canopy")

        // Act
        val encoded = Toml.toString(values, config = { explicitNulls = false })
        val decoded = Toml.fromString<Map<String, String>>(encoded, config = { ignoreUnknownKeys = false })

        // Assert
        assertEquals(values, decoded)
    }
}
