package io.canopy.engine.data.core.parsers

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for [Toml].
 *
 * Important constraints:
 * - TOML has no `null` literal. Strings like `name = null` are invalid TOML.
 * - TomlParser defaults to `explicitNulls = false`, which means:
 *   - decoding: missing keys map to defaults / nullables
 *   - encoding: nullable fields that are null are typically *omitted*, not written as `null`
 */
class TomlTests {

    // --- Fixtures -----------------------------------------------------------

    @Serializable
    data class SimpleConfig(val name: String, val enabled: Boolean = true, val retries: Int = 0)

    @Serializable
    data class NullableConfig(val name: String?, val enabled: Boolean = true, val retries: Int = 0)

    @Serializable
    data class WithArray(val values: List<Int>)

    @Serializable
    data class Nested(val server: Server) {
        @Serializable
        data class Server(val host: String, val port: Int)
    }

    // --- Strict / default behavior -----------------------------------------

    @org.junit.jupiter.api.Nested
    @DisplayName("Default TOML parsing behavior")
    inner class DefaultTomlParsingTests {

        @Test
        fun `fromString parses valid toml`() {
            // Verifies basic decoding.
            val toml = """
                name = "canopy"
                enabled = true
                retries = 3
            """.trimIndent()

            val cfg = Toml.fromString<SimpleConfig>(toml)

            assertEquals("canopy", cfg.name)
            assertTrue(cfg.enabled)
            assertEquals(3, cfg.retries)
        }

        @Test
        fun `fromString applies default values when keys are missing`() {
            // Missing keys should map to default constructor values.
            val toml = """name = "canopy""""

            val cfg = Toml.fromString<SimpleConfig>(toml)

            assertEquals("canopy", cfg.name)
            assertTrue(cfg.enabled) // default
            assertEquals(0, cfg.retries) // default
        }

        @Test
        fun `fromString rejects invalid toml null literal`() {
            // TOML has no null literal; `name = null` should fail parsing/decoding.
            val toml = """
                name = null
                enabled = true
                retries = 1
            """.trimIndent()

            assertThrows<Exception> {
                Toml.fromString<SimpleConfig>(toml)
            }
        }

        @Test
        fun `fromString rejects mixed-type arrays`() {
            // TOML arrays must be homogeneous.
            val toml = """values = [1, "two", 3]"""

            assertThrows<Exception> {
                Toml.fromString<WithArray>(toml)
            }
        }

        @Test
        fun `fromString rejects duplicate keys`() {
            // Duplicate keys should not be allowed in a single table.
            val toml = """
                name = "a"
                name = "b"
                enabled = true
                retries = 0
            """.trimIndent()

            assertThrows<Exception> {
                Toml.fromString<SimpleConfig>(toml)
            }
        }

        @Test
        fun `fromString parses nested tables`() {
            // Verifies decoding TOML tables into nested @Serializable structures.
            val toml = """
                [server]
                host = "localhost"
                port = 8080
            """.trimIndent()

            val cfg = Toml.fromString<Nested>(toml)

            assertEquals("localhost", cfg.server.host)
            assertEquals(8080, cfg.server.port)
        }

        @Test
        fun `toString encodes and decodes roundtrip`() {
            // Verifies encode -> decode stability.
            val original = SimpleConfig(name = "canopy", enabled = false, retries = 7)

            val encoded = Toml.toString(original)
            val decoded = Toml.fromString<SimpleConfig>(encoded)

            assertEquals(original, decoded)

            // Sanity check: encoded output includes the important fields.
            assertTrue(encoded.contains("""name = "canopy""""))
            assertTrue(encoded.contains("enabled = false"))
            assertTrue(encoded.contains("retries = 7"))
        }

        @Test
        fun `toString omits null fields when explicitNulls is false`() {
            // With explicitNulls=false, serializers typically omit null fields rather than writing `null`.
            @Serializable
            data class NullableField(val maybe: String? = null)

            val original = NullableField(null)

            val encoded = Toml.toString(original)

            // We should NOT emit `maybe = null` (invalid TOML).
            assertFalse(encoded.contains("maybe"))

            // Roundtrip should keep null.
            val decoded = Toml.fromString<NullableField>(encoded)
            assertEquals(original, decoded)
        }
    }

    // --- "Non-strict" customization ----------------------------------------
    //
    // NOTE: TOML itself doesn't support null literals. The safest "lenient" behavior you can test is
    // that missing keys can map to nullable properties / defaults.

    @org.junit.jupiter.api.Nested
    @DisplayName("Custom config behavior")
    inner class CustomTomlParsingTests {

        @Test
        fun `missing nullable key decodes as null`() {
            val toml = """
                enabled = true
                retries = 1
            """.trimIndent()

            val cfg = Toml.fromString<NullableConfig>(toml)

            assertEquals(null, cfg.name)
            assertTrue(cfg.enabled)
            assertEquals(1, cfg.retries)
        }
    }
}
