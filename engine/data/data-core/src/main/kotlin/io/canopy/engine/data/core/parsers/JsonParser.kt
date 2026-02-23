package io.canopy.engine.data.core.parsers

import com.badlogic.gdx.files.FileHandle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * Utility for serializing and parsing JSON data using kotlinx.serialization.
 * Supports custom serializers modules for polymorphic data.
 */
object JsonParser {

    inline fun <reified T> fromFile(
        file: FileHandle,
        module: SerializersModule? = null,
        noinline config: JsonBuilder.() -> Unit = {},
    ): T = fromString(file.readString(), module, config)

    inline fun <reified T> fromString(
        jsonString: String,
        module: SerializersModule? = null,
        noinline config: JsonBuilder.() -> Unit = {},
    ): T {
        val json = Json {
            if (module != null) serializersModule = module
            classDiscriminator = "type"
            ignoreUnknownKeys = true
            prettyPrint = true
            prettyPrintIndent = "  "
            config()
        }
        return json.decodeFromString(jsonString)
    }

    fun rawParseFile(
        file: FileHandle,
        module: SerializersModule? = null,
        config: JsonBuilder.() -> Unit = {},
    ): JsonObject {
        val jsonString = file.readString()
        return fromString<JsonElement>(jsonString, module, config).jsonObject
    }

    // -------------------------------
    // Serialization
    // -------------------------------

    /**
     * Serializes a [JsonObject] into a JSON string.
     */
    inline fun <reified T> toString(
        obj: T,
        module: SerializersModule? = null,
        noinline config: JsonBuilder.() -> Unit = {},
    ): String {
        val json = Json {
            if (module != null) serializersModule = module
            classDiscriminator = "type"
            ignoreUnknownKeys = true
            prettyPrint = true
            prettyPrintIndent = "  "
            config()
        }
        return json.encodeToString(obj)
    }

    /**
     * Serializes a [JsonObject] and writes it to the given [file].
     */
    inline fun <reified T> toFile(
        obj: T,
        file: FileHandle,
        module: SerializersModule? = null,
        noinline config: JsonBuilder.() -> Unit = {},
    ) {
        val jsonString = toString(obj, module, config)
        file.writeString(jsonString, false)
    }

    inline fun <reified T> decodeJsonElement(serializer: KSerializer<T> = serializer(), element: JsonElement): T =
        Json.decodeFromJsonElement(serializer, element)

    inline fun <reified T> encodeJsonElement(serializer: KSerializer<T> = serializer(), data: T): JsonElement =
        Json.encodeToJsonElement(serializer, data)
}
