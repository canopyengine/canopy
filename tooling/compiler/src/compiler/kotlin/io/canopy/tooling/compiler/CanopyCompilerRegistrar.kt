@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.canopy.tooling.compiler

import java.util.ServiceLoader
import io.canopy.tooling.compiler.rules.RuleProvider
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration

/** Installs mandatory checks and compiler-only providers before the fixed lowering pipeline. */
class CanopyCompilerRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "io.github.canopyengine.compiler"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messages = DiagnosticMessages(
            configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE),
            configuration.get(DiagnosticOptions.color) ?: false,
            configuration.get(DiagnosticOptions.root)
        )
        val registry = try {
            val loader = this@CanopyCompilerRegistrar.javaClass.classLoader
            RuleRegistry(
                listOf(BuiltinRules()) + ServiceLoader.load(RuleProvider::class.java, loader).toList(),
                ServiceLoader.load(CanopyCompilerRule::class.java, loader).toList()
            )
        } catch (failure: Exception) {
            messages.emit(
                "CANOPY_RULE_ID_CONFLICT",
                "Cannot register compiler rules",
                label = failure.message ?: failure.javaClass.simpleName,
                help = "Check provider API versions, unique IDs, diagnostic ownership and rule targets."
            )
            return
        } catch (failure: java.util.ServiceConfigurationError) {
            messages.emit(
                "CANOPY_RULE_PROVIDER",
                "Cannot load a compiler rule provider",
                label = failure.message,
                help = "Check the provider's service entry and public no-argument constructor."
            )
            return
        }
        IrGenerationExtension.registerExtension(CompilerPipeline(messages, registry))
    }
}
