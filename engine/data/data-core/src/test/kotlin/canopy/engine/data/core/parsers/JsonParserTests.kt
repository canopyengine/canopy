package canopy.engine.data.core.parsers

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

class JsonParserTests {
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

    @Nested
    @DisplayName("Simple tests for JsonParser")
    inner class SimpleJsonParserTests {
        @Test
        fun `should parse JSON string into data class`() {
            val jsonString = """{"id":1,"name":"Test"}"""
            val parsedData = JsonParser.fromString<SimpleData>(jsonString)
            assert(parsedData.id == 1)
            assert(parsedData.name == "Test")
        }

        @Test
        fun `should parse JSON string with unknown keys`() {
            val jsonString = """{"id":2,"name":"Unknown","extraField":"ignored"}"""
            val parsedData = JsonParser.fromString<SimpleData>(jsonString)
            assert(parsedData.id == 2)
            assert(parsedData.name == "Unknown")
        }

        @Test
        fun `should parse list of data classes from JSON string`() {
            val jsonString = """[{"id":3,"name":"Item1"},{"id":4,"name":"Item2"}]"""
            val parsedData = JsonParser.fromString<List<SimpleData>>(jsonString)
            assert(parsedData.size == 2)
            assert(parsedData[0].id == 3 && parsedData[0].name == "Item1")
            assert(parsedData[1].id == 4 && parsedData[1].name == "Item2")
        }
    }

    @Nested
    @DisplayName("Parsing with polymorphic types")
    inner class PolymorphicJsonParserTests {
        val module =
            SerializersModule {
                polymorphic(BaseType::class) {
                    subclass(ImplementationA::class)
                    subclass(ImplementationB::class)
                }
            }

        @Test
        fun `should parse polymorphic JSON string into correct subclass`() {
            val jsonStringA =
                """
                {
                    "type": "ImplementationA",
                    "valueA": "Hello"
                }
                """.trimIndent()
            val parsedA = JsonParser.fromString<BaseType>(jsonStringA, module)
            assert(parsedA is ImplementationA)
            assert((parsedA as ImplementationA).valueA == "Hello")
        }

        @Test
        fun `should parse list of polymorphic types from JSON string`() {
            val jsonString = """[
                {
                    "type": "ImplementationA",
                    "valueA": "First"
                },
                {
                    "type": "ImplementationB",
                    "valueB": 42
                }
            ]"""
            val parsedList = JsonParser.fromString<List<BaseType>>(jsonString, module)
            assertEquals(2, parsedList.size)

            val itemA = parsedList[0]
            assertIs<ImplementationA>(itemA)
            assertEquals("First", itemA.valueA)

            val itemB = parsedList[1]
            assertIs<ImplementationB>(itemB)
            assertEquals(42, itemB.valueB)
        }
    }
}
