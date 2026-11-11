@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.canopy.tooling.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

/** Presentation-only options supplied by the Gradle host; rules and severities never change. */
class DiagnosticOptions : CommandLineProcessor {
    override val pluginId = "io.github.canopyengine.compiler"
    override val pluginOptions = listOf(
        CliOption("diagnosticColor", "true|false", "Color Canopy diagnostic bodies", required = false),
        CliOption("diagnosticRoot", "path", "Root for displayed source paths", required = false)
    )
    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            "diagnosticColor" -> configuration.put(
                color,
                value.toBooleanStrictOrNull()
                    ?: throw CliOptionProcessingException("Canopy diagnosticColor must be true or false")
            )
            "diagnosticRoot" -> configuration.put(root, value)
            else -> error("Unknown Canopy diagnostic option: ${option.optionName}")
        }
    }
    internal companion object {
        val color = CompilerConfigurationKey<Boolean>("Canopy diagnostic colors")
        val root = CompilerConfigurationKey<String>("Canopy source root")
    }
}
