package io.canopy.engine.data.parsers

import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlConfigBuilder
import dev.eav.tomlkt.decodeFromString
import io.canopy.engine.data.assets.AssetEntry
import io.canopy.engine.data.assets.WritableAssetEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.modules.SerializersModule

/** TOML serialization helpers with Canopy defaults; caller configuration overrides those defaults. */
object Toml {

    /** Reads and decodes the entry text using the selected serializers and configuration. */
    inline fun <reified T> fromFile(
        file: AssetEntry,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ): T = fromString(file.readText(), module, config)

    /** Decodes TOML text; parsing and serialization errors propagate to the caller. */
    inline fun <reified T> fromString(
        tomlString: String,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ): T = buildToml(module, config).decodeFromString(tomlString)

    /** Encodes a value as TOML text. */
    inline fun <reified T> toString(
        obj: T,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ): String = buildToml(module, config).encodeToString(obj)

    /** Encodes a value and overwrites the writable entry. */
    inline fun <reified T> toFile(
        obj: T,
        file: WritableAssetEntry,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ) {
        file.writeText(toString(obj, module, config), append = false)
    }

    /**
     * Creates a codec using type as the discriminator, ignoring unknown keys and omitting explicit nulls by
     * default.
     */
    fun buildToml(module: SerializersModule? = null, config: TomlConfigBuilder.() -> Unit = {}) = Toml {
        if (module != null) serializersModule = module

        classDiscriminator = "type"
        ignoreUnknownKeys = true
        explicitNulls = false

        config()
    }
}
