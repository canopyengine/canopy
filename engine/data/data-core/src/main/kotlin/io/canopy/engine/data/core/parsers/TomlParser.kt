package io.canopy.engine.data.core.parsers

import com.badlogic.gdx.files.FileHandle
import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlConfigBuilder
import dev.eav.tomlkt.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.modules.SerializersModule

/**
 * Utility for serializing and parsing TOML data using ktoml + kotlinx.serialization.
 * STRICT (TOML-compliant) by default.
 */
object TomlParser {

    /**
     * Parses a TOML file into an instance of the specified type [T].
     */
    inline fun <reified T> fromFile(
        file: FileHandle,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ): T = fromString<T>(file.readString(), module, config)

    /**
     * Parses a TOML string into an instance of the specified type [T].
     */
    inline fun <reified T> fromString(
        tomlString: String,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ): T {
        val toml = Toml {
            if (module != null) serializersModule = module
            classDiscriminator = "type"
            ignoreUnknownKeys = true
            explicitNulls = false // TOML spec doesn't support null; treat missing keys as null/defaults.
            config()
        }
        return toml.decodeFromString(tomlString)
    }

    // -------------------------------
    // Serialization
    // -------------------------------

    /**
     * Serializes an object of type [T] into a serialized [String].
     */
    inline fun <reified T> toString(
        obj: T,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ): String {
        val toml = Toml {
            if (module != null) serializersModule = module
            classDiscriminator = "type"
            ignoreUnknownKeys = true
            explicitNulls = false // TOML spec doesn't support null; treat missing keys as null/defaults.
            config()
        }
        return toml.encodeToString(obj)
    }

    /**
     * Serializes an object of type [T] and writes it to the given [file].
     */
    inline fun <reified T> toFile(obj: T, file: FileHandle, module: SerializersModule? = null) {
        val tomlString = toString(obj, module)
        file.writeString(tomlString, false)
    }
}
