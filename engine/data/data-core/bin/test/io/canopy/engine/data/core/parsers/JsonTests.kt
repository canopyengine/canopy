package io.canopy.engine.data.core.parsers

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [Json].
 *
 * JsonParser defaults (important for these tests):
 * - ignoreUnknownKeys = true
 * - classDiscriminator = "type" for polymorphic decoding
 */
class JsonTests {

    // --- Test fixtures -------------------------------------------------------

    @Serializable
    data class SimpleData(val id: Int, val name: String)

    @Serializable
    sealed interface BaseType

    @Serializable
    @SerialName("ImplementationA")
    data class ImplementationA(val valueA: String) : BaseType

    @Serializable
    @SerialName("ImplementationB")
    data class ImplementationB(val valueB: Int) : BaseType

    // --- Simple decoding -----------------------------------------------------

    @Nested
    @DisplayName("Simple decoding")
    inner class SimpleJsonParserTests {

        @Test
        fun `should parse JSON string into data class`() {
            // Verifies basic decode from a JSON object into a Kotlin @Serializable type.
            val jsonString = """{"id":1,"name":"Test"}"""

            val parsed = Json.fromString<SimpleData>(jsonString)

            assertEquals(1, parsed.id)
            assertEquals("Test", parsed.name)
        }

        @Test
        fun `should ignore unknown keys by default`() {
            // JsonParser is configured with ignoreUnknownKeys = true.
            val jsonString = """{"id":2,"name":"Unknown","extraField":"ignored"}"""

            val parsed = Json.fromString<SimpleData>(jsonString)

            assertEquals(2, parsed.id)
            assertEquals("Unknown", parsed.name)
        }

        @Test
        fun `should parse list of data classes from JSON string`() {
            // Verifies decoding generic collections works (reified type).
            val jsonString = """[{"id":3,"name":"Item1"},{"id":4,"name":"Item2"}]"""

            val parsed = Json.fromString<List<SimpleData>>(jsonString)

            assertEquals(2, parsed.size)

            assertEquals(3, parsed[0].id)
            assertEquals("Item1", parsed[0].name)

            assertEquals(4, parsed[1].id)
            assertEquals("Item2", parsed[1].name)
        }
    }

    // --- Polymorphic decoding ------------------------------------------------

    @Nested
    @DisplayName("Polymorphic decoding")
    inner class PolymorphicJsonParserTests {

        /**
         * Polymorphic module used for these tests.
         *
         * JsonParser uses `classDiscriminator = "type"`,
         * so the JSON must contain `"type": "<SerialName>"`.
         */
        private val module = SerializersModule {
            polymorphic(BaseType::class) {
                subclass(ImplementationA::class)
                subclass(ImplementationB::class)
            }
        }

        @Test
        fun `should parse polymorphic JSON string into correct subclass`() {
            val jsonString = """
                {
                  "type": "ImplementationA",
                  "valueA": "Hello"
                }
            """.trimIndent()

            val parsed = Json.fromString<BaseType>(jsonString, module)

            val a = assertIs<ImplementationA>(parsed)
            assertEquals("Hello", a.valueA)
        }

        @Test
        fun `should parse list of polymorphic types from JSON string`() {
            val jsonString = """
                [
                  { "type": "ImplementationA", "valueA": "First" },
                  { "type": "ImplementationB", "valueB": 42 }
                ]
            """.trimIndent()

            val parsed = Json.fromString<List<BaseType>>(jsonString, module)

            assertEquals(2, parsed.size)

            val itemA = assertIs<ImplementationA>(parsed[0])
            assertEquals("First", itemA.valueA)

            val itemB = assertIs<ImplementationB>(parsed[1])
            assertEquals(42, itemB.valueB)
        }
    }
}
