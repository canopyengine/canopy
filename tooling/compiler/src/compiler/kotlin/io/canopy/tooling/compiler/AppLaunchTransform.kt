@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.canopy.tooling.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.resolveFakeOverride
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/** Routes processed startup calls through matching runtime entry points; never installs a global presence flag. */
internal class AppLaunchTransform(private val messages: DiagnosticMessages) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val app = pluginContext.referenceClass(ClassId.topLevel(FqName("io.canopy.engine.app.App")))?.owner ?: return
        val entries = app.functions.filter { it.name.asString() in setOf("launch", "launchAsync") }.toList()
        val targets = entries.associate { entry ->
            entry.symbol to app.functions.singleOrNull {
                it.name.asString() == "compiler" + entry.name.asString().replaceFirstChar(Char::uppercaseChar) &&
                    it.parameters.size == entry.parameters.size &&
                    it.returnType == entry.returnType &&
                    it.parameters.zip(entry.parameters).all { (a, b) -> a.type == b.type }
            }?.symbol
        }
        fun target(function: IrSimpleFunction): IrSimpleFunctionSymbol? {
            val original = function.resolveFakeOverride() ?: function
            if (original.symbol !in targets) return null
            val target = targets[original.symbol]
            if (target == null) {
                messages.legacy("CANOPY_APP_ABI", "Application startup requires matching runtime ABI helpers.")
            }
            return target
        }
        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                expression.transformChildrenVoid(this)
                target(expression.symbol.owner)?.let { expression.symbol = it }
                return expression
            }

            override fun visitFunctionReference(expression: IrFunctionReference): IrExpression {
                expression.transformChildrenVoid(this)
                val function = expression.symbol.owner as? IrSimpleFunction ?: return expression
                target(function)?.let {
                    expression.symbol = it
                    // Keep the public name for Kotlin reflection; invocation uses the processed entry.
                }
                return expression
            }
        })
    }
}
