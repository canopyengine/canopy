package io.canopy.engine.data.parsers

import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlConfigBuilder
import dev.eav.tomlkt.decodeFromString
import io.canopy.engine.data.assets.AssetEntry
import io.canopy.engine.data.assets.WritableAssetEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.modules.SerializersModule

object Toml {

    inline fun <reified T> fromFile(
        file: AssetEntry,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ): T = fromString(file.readText(), module, config)

    inline fun <reified T> fromString(
        tomlString: String,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ): T = buildToml(module, config).decodeFromString(tomlString)

    inline fun <reified T> toString(
        obj: T,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ): String = buildToml(module, config).encodeToString(obj)

    inline fun <reified T> toFile(
        obj: T,
        file: WritableAssetEntry,
        module: SerializersModule? = null,
        noinline config: TomlConfigBuilder.() -> Unit = {},
    ) {
        file.writeText(toString(obj, module, config), append = false)
    }

    fun buildToml(module: SerializersModule? = null, config: TomlConfigBuilder.() -> Unit = {}) = Toml {
        if (module != null) serializersModule = module

        classDiscriminator = "type"
        ignoreUnknownKeys = true
        explicitNulls = false

        config()
    }
}
