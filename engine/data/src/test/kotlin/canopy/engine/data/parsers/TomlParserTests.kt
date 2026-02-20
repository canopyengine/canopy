package canopy.engine.data.parsers

import com.akuleshov7.ktoml.exceptions.TomlDecodingException
import com.akuleshov7.ktoml.exceptions.TomlEncodingException
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TomlParserTests {

    @Serializable
    data class SimpleConfig(val name: String, val enabled: Boolean = true, val retries: Int = 0)

    @Serializable
    data class WithArray(val values: List<Int>)

    @Serializable
    data class Nested(val server: Server) {
        @Serializable
        data class Server(val host: String, val port: Int)
    }

    @Test
    fun `parseString - strict - parses valid toml`() {
        val toml = """
            name = "canopy"
            enabled = true
            retries = 3
        """.trimIndent()

        val cfg = TomlParser.parseString<SimpleConfig>(toml)

        assertEquals("canopy", cfg.name)
        assertTrue(cfg.enabled)
        assertEquals(3, cfg.retries)
    }

    @Test
    fun `parseString - strict - default values are applied`() {
        val toml = """
            name = "canopy"
        """.trimIndent()

        val cfg = TomlParser.parseString<SimpleConfig>(toml)

        assertEquals("canopy", cfg.name)
        assertTrue(cfg.enabled) // default
        assertEquals(0, cfg.retries) // default
    }

    @Test
    fun `parseString - strict - rejects null values`() {
        // TOML spec doesn't support null; strict/compliant mode should reject it.
        val toml = """
            name = null
            enabled = true
            retries = 1
        """.trimIndent()

        // Depending on where it fails, ktoml may throw parsing or decoding exceptions.
        assertThrows<Exception> {
            TomlParser.parseString<SimpleConfig>(toml)
        }.also { ex ->
            assertTrue(ex is TomlEncodingException || ex is TomlDecodingException)
        }
    }

    @Test
    fun `parseString - strict - rejects mixed-type arrays`() {
        val toml = """
            values = [1, "two", 3]
        """.trimIndent()

        assertThrows<Exception> {
            TomlParser.parseString<WithArray>(toml)
        }.also { ex ->
            assertTrue(ex is TomlEncodingException || ex is TomlDecodingException)
        }
    }

    @Test
    fun `parseString - strict - rejects duplicate keys`() {
        val toml = """
            name = "a"
            name = "b"
            enabled = true
            retries = 0
        """.trimIndent()

        assertThrows<TomlEncodingException> {
            TomlParser.parseString<SimpleConfig>(toml)
        }
    }

    @Test
    fun `parseString - strict - parses nested tables`() {
        val toml = """
            [server]
            host = "localhost"
            port = 8080
        """.trimIndent()

        val cfg = TomlParser.parseString<Nested>(toml)

        assertEquals("localhost", cfg.server.host)
        assertEquals(8080, cfg.server.port)
    }

    @Test
    fun `toString - strict - encodes and decodes roundtrip`() {
        val original = SimpleConfig(name = "canopy", enabled = false, retries = 7)

        val encoded = TomlParser.toString(original)
        val decoded = TomlParser.parseString<SimpleConfig>(encoded)

        assertEquals(original, decoded)
        // basic sanity: output should include required fields
        assertTrue(encoded.contains("""name = "canopy""""))
        assertTrue(encoded.contains("enabled = false"))
        assertTrue(encoded.contains("retries = 7"))
    }

    @Test
    fun `toString - strict - cannot encode data that requires null`() {
        @Serializable
        data class NullableField(val maybe: String? = null)

        val value = NullableField(null)

        // In strict/compliant mode, emitting null should fail (TOML has no null).
        assertThrows<Exception> {
            TomlParser.toString(value)
        }.also { ex ->
            // ktoml may throw encoding/illegal-state exceptions depending on version
            assertNotNull(ex)
        }
    }
}
