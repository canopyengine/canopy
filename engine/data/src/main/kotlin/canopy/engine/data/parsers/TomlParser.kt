package canopy.engine.data.parsers

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.parsers.TomlParser
import com.akuleshov7.ktoml.tree.nodes.TomlFile
import com.akuleshov7.ktoml.writers.TomlWriter
import com.badlogic.gdx.files.FileHandle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * Utility for serializing and parsing TOML data using ktoml + kotlinx.serialization.
 * STRICT (TOML-compliant) by default.
 */
object TomlParser {

    val simpleToml: Toml = createToml()

    fun createToml(
        module: SerializersModule? = null,
        inputConfig: TomlInputConfig = TomlInputConfig.compliant(),
        outputConfig: TomlOutputConfig = TomlOutputConfig.compliant(),
    ): Toml = Toml(
        inputConfig = inputConfig,
        outputConfig = outputConfig,
        serializersModule = module ?: EmptySerializersModule()
    )

    // -------------------------------
    // Deserialization (schema-based)
    // -------------------------------

    inline fun <reified T> parseFile(
        file: FileHandle,
        module: SerializersModule? = null,
        inputConfig: TomlInputConfig = TomlInputConfig.compliant(),
        outputConfig: TomlOutputConfig = TomlOutputConfig.compliant(),
    ): T {
        val toml = createToml(module, inputConfig, outputConfig)
        return toml.decodeFromString(serializer(), file.readString())
    }

    inline fun <reified T> parseString(
        tomlString: String,
        module: SerializersModule? = null,
        inputConfig: TomlInputConfig = TomlInputConfig.compliant(),
        outputConfig: TomlOutputConfig = TomlOutputConfig.compliant(),
    ): T {
        val toml = createToml(module, inputConfig, outputConfig)
        return toml.decodeFromString(serializer(), tomlString)
    }

    // -------------------------------
    // Raw parsing (AST)
    // -------------------------------

    fun rawParseFile(file: FileHandle, inputConfig: TomlInputConfig = TomlInputConfig.compliant()): TomlFile =
        TomlParser(inputConfig).parseString(file.readString())

    fun rawParseString(tomlString: String, inputConfig: TomlInputConfig = TomlInputConfig.compliant()): TomlFile =
        TomlParser(inputConfig).parseString(tomlString)

    // -------------------------------
    // Serialization (schema-based)
    // -------------------------------

    inline fun <reified T> toString(
        value: T,
        module: SerializersModule? = null,
        inputConfig: TomlInputConfig = TomlInputConfig.compliant(),
        outputConfig: TomlOutputConfig = TomlOutputConfig.compliant(),
    ): String {
        val toml = createToml(module, inputConfig, outputConfig)
        return toml.encodeToString(serializer(), value)
    }

    inline fun <reified T> toFile(
        value: T,
        file: FileHandle,
        module: SerializersModule? = null,
        inputConfig: TomlInputConfig = TomlInputConfig.compliant(),
        outputConfig: TomlOutputConfig = TomlOutputConfig.compliant(),
    ) {
        file.writeString(toString(value, module, inputConfig, outputConfig), false)
    }

    // -------------------------------
    // AST <-> object helpers
    // -------------------------------

    fun <T> decodeTomlAst(
        serializer: KSerializer<T>,
        ast: TomlFile,
        module: SerializersModule? = null,
        inputConfig: TomlInputConfig = TomlInputConfig.compliant(),
        outputConfig: TomlOutputConfig = TomlOutputConfig.compliant(),
    ): T {
        val toml = createToml(module, inputConfig, outputConfig)
        val tomlString = TomlWriter(outputConfig).writeToString(ast)
        return toml.decodeFromString(serializer, tomlString)
    }

    fun <T> encodeTomlAst(
        serializer: KSerializer<T>,
        value: T,
        module: SerializersModule? = null,
        inputConfig: TomlInputConfig = TomlInputConfig.compliant(),
        outputConfig: TomlOutputConfig = TomlOutputConfig.compliant(),
    ): TomlFile {
        val toml = createToml(module, inputConfig, outputConfig)
        val tomlString = toml.encodeToString(serializer, value)
        return TomlParser(inputConfig).parseString(tomlString)
    }

    inline fun <reified T> decodeTomlAst(ast: TomlFile, module: SerializersModule? = null): T =
        decodeTomlAst(serializer<T>(), ast, module)

    inline fun <reified T> encodeTomlAst(value: T, module: SerializersModule? = null): TomlFile =
        encodeTomlAst(serializer<T>(), value, module)
}
