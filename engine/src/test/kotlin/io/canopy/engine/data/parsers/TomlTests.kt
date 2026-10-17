package io.canopy.engine.data.core.parsers

import kotlin.test.assertFailsWith
import io.canopy.engine.data.parsers.Toml
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [io.canopy.engine.data.parsers.Toml].
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
        fun `malformed TOML propagates decoding failures`() {
            val invalidInputs = listOf(
                "null literal" to { Toml.fromString<SimpleConfig>("name = null") },
                "mixed array" to { Toml.fromString<WithArray>("values = [1, \"two\", 3]") },
                "duplicate keys" to { Toml.fromString<SimpleConfig>("name = \"a\"\nname = \"b\"") }
            )
            invalidInputs.forEach { (label, decode) ->
                assertFailsWith<Exception>(label) { decode() }
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
    @DisplayName("Nullable values and caller configuration")
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

        @Test
        fun `caller configuration can reject unknown keys accepted by default`() {
            val input = "name = \"canopy\"\nextra = 1"
            assertEquals("canopy", Toml.fromString<SimpleConfig>(input).name)

            assertFailsWith<Exception> {
                Toml.fromString<SimpleConfig>(input, config = { ignoreUnknownKeys = false })
            }
        }
    }
}
