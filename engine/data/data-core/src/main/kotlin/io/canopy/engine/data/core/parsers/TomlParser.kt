package io.canopy.engine.data.core.parsers

import com.badlogic.gdx.files.FileHandle
import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlConfigBuilder
import dev.eav.tomlkt.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.modules.SerializersModule

/**
 * TOML helper built on top of tomlkt + kotlinx.serialization.
 *
 * Primary use cases:
 * - Read engine/app configuration files written in TOML
 * - Write configuration back to disk in a TOML-friendly format
 *
 * Defaults (applied unless overridden via [config]):
 * - `classDiscriminator = "type"`: polymorphic values use `"type"` to select the subtype
 * - `ignoreUnknownKeys = true`: forward-compatible parsing (extra fields are ignored)
 * - `explicitNulls = false`: TOML has no null literal; missing keys are treated as absent/defaults
 *
 * Note on "STRICT":
 * tomlkt parses TOML according to the TOML specification. This parser does not attempt
 * to accept non-standard TOML extensions by default.
 */
object TomlParser {

    /* ============================================================
     * Decoding
     * ============================================================ */

    /**
     * Reads a TOML file and decodes it into [T].
     *
     * @param file Source file handle
     * @param module Optional serializers module for polymorphic/custom serializers
     * @param config Optional tomlkt config customization (applied last; can override defaults)
     */
    inline fun <reified T> fromFile(
        file: FileHandle,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ): T = fromString(file.readString(), module, config)

    /**
     * Decodes a TOML string into [T].
     */
    inline fun <reified T> fromString(
        tomlString: String,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ) = buildToml(module, config).decodeFromString<T>(tomlString)

    /* ============================================================
     * Encoding
     * ============================================================ */

    /**
     * Serializes [obj] into a TOML string.
     *
     * @param obj Object to serialize
     * @param module Optional serializers module for polymorphic/custom serializers
     * @param config Optional tomlkt config customization (applied last; can override defaults)
     */
    inline fun <reified T> toString(
        obj: T,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ) = buildToml(module, config).encodeToString(obj)

    /**
     * Serializes [obj] and writes it to [file], replacing existing contents.
     */
    inline fun <reified T> toFile(obj: T, file: FileHandle, module: SerializersModule? = null) {
        val tomlString = toString(obj, module)
        file.writeString(tomlString, false)
    }

    fun buildToml(module: SerializersModule? = null, config: TomlConfigBuilder.() -> Unit) = Toml {
        if (module != null) serializersModule = module

        classDiscriminator = "type"
        ignoreUnknownKeys = true
        explicitNulls = false

        config()
    }
}
