@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.canopy.tooling.compiler

import java.util.ServiceLoader
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/** Installs mandatory engine checks and additional rule providers from the compiler plugin classpath. */
class CanopyCompilerRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "io.canopy.compiler"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messages = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        val rules = listOf(NodeStateRule()) +
            ServiceLoader.load(
                CanopyCompilerRule::class.java,
                this@CanopyCompilerRegistrar.javaClass.classLoader
            ).toList()
        require(rules.map { it.id }.distinct().size == rules.size) { "Canopy compiler rule IDs must be unique" }
        IrGenerationExtension.registerExtension(NodePropertyTransform(messages))
        IrGenerationExtension.registerExtension(CanopyRuleRunner(messages, rules))
        IrGenerationExtension.registerExtension(NodeConstructionTransform(messages))
    }
}

/** Traverses each declaration once and shares compilation-local services across all registered rules. */
private class CanopyRuleRunner(private val messages: MessageCollector, private val rules: List<CanopyCompilerRule>) :
    IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val context = CanopyRuleContext(moduleFragment, pluginContext, messages)
        moduleFragment.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitDeclaration(declaration: IrDeclarationBase) {
                rules.forEach { it.check(declaration, context) }
                declaration.acceptChildrenVoid(this)
            }
        })
    }
}
