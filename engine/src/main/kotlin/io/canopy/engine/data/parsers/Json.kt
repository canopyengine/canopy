package io.canopy.engine.data.core.parsers

import com.badlogic.gdx.files.FileHandle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * JSON helper built on top of kotlinx.serialization.
 *
 * What this utility does:
 * - Decodes JSON strings/files into Kotlin types (kotlinx.serialization)
 * - Encodes Kotlin types into JSON strings/files
 * - Allows optional [SerializersModule] for polymorphic / custom serializers
 *
 * Default JSON configuration (applied to all helpers unless overridden via [config]):
 * - `classDiscriminator = "type"`: polymorphic payloads use `"type"` to select the subtype
 * - `ignoreUnknownKeys = true`: forward-compatible parsing (extra fields are ignored)
 * - pretty printing enabled (useful for config files written back to disk)
 *
 * Note:
 * The [config] lambda is applied last, so callers can override any default.
 */
object Json {

    /* ============================================================
     * Decoding
     * ============================================================ */

    /**
     * Reads a file and decodes it into [T].
     *
     * @param file Source file
     * @param module Optional serializers module (polymorphism/custom serializers)
     * @param config Optional JSON builder customization (overrides defaults)
     */
    inline fun <reified T> fromFile(
        file: FileHandle,
        module: SerializersModule? = null,
        noinline config: JsonBuilder.() -> Unit = {},
    ): T = fromString(file.readString(), module, config)

    /**
     * Decodes a JSON string into [T].
     */
    inline fun <reified T> fromString(
        jsonString: String,
        module: SerializersModule? = null,
        noinline config: JsonBuilder.() -> Unit = {},
    ) = buildJson(module, config).decodeFromString<T>(jsonString)

    /**
     * Parses a JSON file into a raw [JsonObject].
     *
     * This is useful for:
     * - inspection
     * - manual extraction
     * - patch/transform operations before decoding into a concrete type
     *
     * @throws IllegalStateException if the root element is not a JSON object
     */
    fun rawParseFile(
        file: FileHandle,
        module: SerializersModule? = null,
        config: JsonBuilder.() -> Unit = {},
    ): JsonObject {
        val jsonString = file.readString()
        return fromString<JsonElement>(jsonString, module, config).jsonObject
    }

    /* ============================================================
     * Encoding
     * ============================================================ */

    /**
     * Serializes [obj] into a JSON string.
     *
     * @param obj Object to serialize
     * @param module Optional serializers module (polymorphism/custom serializers)
     * @param config Optional JSON builder customization (overrides defaults)
     */
    inline fun <reified T> toString(
        obj: T,
        module: SerializersModule? = null,
        noinline config: JsonBuilder.() -> Unit = {},
    ) = buildJson(module, config).encodeToString(obj)

    /**
     * Serializes [obj] to JSON and writes it to [file].
     *
     * Note: `append = false` is intentional to replace file contents.
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

    /* ============================================================
     * JsonElement helpers
     * ============================================================ */

    /**
     * Decodes a [JsonElement] into [T] using the provided [serializer].
     *
     * Useful when you already parsed JSON and want to decode only a subtree.
     */
    inline fun <reified T> decodeJsonElement(serializer: KSerializer<T> = serializer(), element: JsonElement): T =
        kotlinx.serialization.json.Json.decodeFromJsonElement(serializer, element)

    /**
     * Encodes [data] into a [JsonElement] using the provided [serializer].
     */
    inline fun <reified T> encodeJsonElement(serializer: KSerializer<T> = serializer(), data: T): JsonElement =
        kotlinx.serialization.json.Json.encodeToJsonElement(serializer, data)

    fun buildJson(module: SerializersModule? = null, config: JsonBuilder.() -> Unit = {}) = Json {
        if (module != null) serializersModule = module

        classDiscriminator = "type"
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "

        config()
    }
}
