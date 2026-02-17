package canopy.data.parsers

import com.badlogic.gdx.files.FileHandle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.modules.SerializersModule

/**
 * Utility for serializing and parsing JSON data using kotlinx.serialization.
 * Supports custom serializers modules for polymorphic data.
 */
object JsonParser {
    val simpleJson = createJson()

    @OptIn(ExperimentalSerializationApi::class)
    fun createJson(module: SerializersModule? = null) = Json {
        if (module != null) serializersModule = module
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    // -------------------------------
    // Deserialization
    // -------------------------------

    inline fun <reified T> parseFile(file: FileHandle, module: SerializersModule? = null): T {
        val json = createJson(module)
        return json.decodeFromString(file.readString())
    }

    inline fun <reified T> parseString(jsonString: String, module: SerializersModule? = null): T {
        val json = createJson(module)
        return json.decodeFromString(jsonString)
    }

    fun rawParseFile(file: FileHandle, module: SerializersModule? = null): JsonObject =
        parseFile<JsonObject>(file, module)

    // -------------------------------
    // Serialization
    // -------------------------------

    /**
     * Serializes a [JsonObject] into a JSON string.
     */
    fun toString(obj: JsonObject, module: SerializersModule? = null): String {
        val json = createJson(module)
        return json.encodeToString(obj)
    }

    /**
     * Serializes a [JsonObject] and writes it to the given [file].
     */
    fun toFile(obj: JsonObject, file: FileHandle, module: SerializersModule? = null) {
        val jsonString = toString(obj, module)
        file.writeString(jsonString, false)
    }

    inline fun <reified T> decodeJsonElement(serializer: KSerializer<T>, element: JsonElement): T =
        simpleJson.decodeFromJsonElement(serializer, element)

    inline fun <reified T> encodeJsonElement(serializer: KSerializer<T>, data: T): JsonElement =
        simpleJson.encodeToJsonElement(serializer, data)
}
