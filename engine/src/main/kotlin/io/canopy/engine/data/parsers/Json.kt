package io.canopy.engine.data.parsers

import io.canopy.engine.data.assets.AssetEntry
import io.canopy.engine.data.assets.WritableAssetEntry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * JSON helper built on top of kotlinx.serialization.
 *
 * What this utility does:
 * - Decodes JSON strings/files into Kotlin types
 * - Encodes Kotlin types into JSON strings/files
 * - Allows optional [SerializersModule] for polymorphic / custom serializers
 *
 * Default JSON configuration:
 * - classDiscriminator = "type"
 * - ignoreUnknownKeys = true
 * - prettyPrint = true
 *
 * The [config] lambda is applied last, so callers can override defaults.
 */
object Json {

    inline fun <reified T> fromFile(
        file: AssetEntry,
        module: SerializersModule? = null,
        noinline config: JsonBuilder.() -> Unit = {},
    ): T = fromString(file.readText(), module, config)

    inline fun <reified T> fromString(
        jsonString: String,
        module: SerializersModule? = null,
        noinline config: JsonBuilder.() -> Unit = {},
    ): T = buildJson(module, config).decodeFromString(jsonString)

    fun rawParseFile(
        file: AssetEntry,
        module: SerializersModule? = null,
        config: JsonBuilder.() -> Unit = {},
    ): JsonObject {
        val jsonString = file.readText()
        return fromString<JsonElement>(jsonString, module, config).jsonObject
    }

    inline fun <reified T> toString(
        obj: T,
        module: SerializersModule? = null,
        noinline config: JsonBuilder.() -> Unit = {},
    ): String = buildJson(module, config).encodeToString(obj)

    inline fun <reified T> toFile(
        obj: T,
        file: WritableAssetEntry,
        module: SerializersModule? = null,
        noinline config: JsonBuilder.() -> Unit = {},
    ) {
        file.writeText(toString(obj, module, config), append = false)
    }

    inline fun <reified T> decodeJsonElement(serializer: KSerializer<T> = serializer(), element: JsonElement): T =
        Json.decodeFromJsonElement(serializer, element)

    inline fun <reified T> encodeJsonElement(serializer: KSerializer<T> = serializer(), data: T): JsonElement =
        Json.encodeToJsonElement(serializer, data)

    fun buildJson(module: SerializersModule? = null, config: JsonBuilder.() -> Unit = {}): Json = Json {
        if (module != null) serializersModule = module

        classDiscriminator = "type"
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "

        config()
    }
}
