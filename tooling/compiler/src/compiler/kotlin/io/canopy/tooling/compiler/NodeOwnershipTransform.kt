@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.canopy.tooling.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Gives implicit Canopy operations their lexical Node, rather than whichever caller happens to be active. */
internal class NodeOwnershipTransform(private val messages: MessageCollector) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val checks = CanopyRuleContext(moduleFragment, pluginContext, messages)
        val node = pluginContext.referenceClass(ClassId.topLevel(FqName("io.canopy.engine.core.nodes.Node")))?.owner
        val boundary = node?.functions?.singleOrNull {
            it.name.asString() == "compilerOwned" &&
                it.typeParameters.size == 1 &&
                it.isInline &&
                it.visibility == DescriptorVisibilities.PROTECTED &&
                it.parameters.size == 2 &&
                it.parameters.last().type.classFqName?.asString() == "kotlin.Function0" &&
                it.returnType == it.typeParameters.single().defaultType
        }
        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {
            private var owner: IrClass? = null
            private var receiver: IrValueParameter? = null
            private var parent: IrDeclarationParent? = null
            private var beforeConstruction = false

            override fun visitClass(declaration: IrClass): IrStatement {
                val previousOwner = owner
                val previousReceiver = receiver
                owner = declaration.takeIf(checks::isNodeSubclass)
                receiver = owner?.thisReceiver
                val result = super.visitClass(declaration)
                owner = previousOwner
                receiver = previousReceiver
                return result
            }

            override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
                val previous = parent
                val previousOwner = owner
                val previousReceiver = receiver
                if (declaration is IrDeclarationParent) parent = declaration
                // UI builders own retained regions; their resources must leave with the region, not the outer Node.
                if (declaration is IrSimpleFunction &&
                    declaration.parameters.any {
                        it.type.classFqName?.asString() == "io.canopy.engine.ui.UiScope"
                    }
                ) {
                    owner = null
                }
                if (declaration is IrSimpleFunction) {
                    val nodeReceiver = declaration.parameters.firstOrNull {
                        it.kind == IrParameterKind.ExtensionReceiver &&
                            it.type.classOrNull?.owner?.let { type -> type === node || checks.isNodeSubclass(type) } ==
                            true
                    }
                    if (nodeReceiver != null) {
                        owner = nodeReceiver.type.classOrNull!!.owner
                        receiver = nodeReceiver
                    } else if (declaration.parent === owner) {
                        receiver = declaration.dispatchReceiverParameter
                    }
                }
                val result = super.visitDeclaration(declaration)
                parent = previous
                owner = previousOwner
                receiver = previousReceiver
                return result
            }

            override fun visitValueParameter(declaration: IrValueParameter): IrStatement {
                val previous = beforeConstruction
                if (declaration.parent is IrConstructor) beforeConstruction = true
                val result = super.visitValueParameter(declaration)
                beforeConstruction = previous
                return result
            }

            override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall): IrExpression {
                val previous = beforeConstruction
                beforeConstruction = true
                val result = super.visitDelegatingConstructorCall(expression)
                beforeConstruction = previous
                return result
            }

            override fun visitCall(expression: IrCall): IrExpression {
                expression.transformChildrenVoid(this)
                val function = expression.symbol.owner
                val name = function.fqNameWhenAvailable?.asString().orEmpty()
                if (!name.startsWith("io.canopy.engine.core.flows.events.") ||
                    function.name.asString() !in setOf("connect", "signal", "computed", "effect", "event", "asSignal")
                ) {
                    return expression
                }
                return protect(expression)
            }

            override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
                expression.transformChildrenVoid(this)
                val name = expression.symbol.owner.parentAsClass.fqNameWhenAvailable?.asString().orEmpty()
                if (name !in setOf(
                        "io.canopy.engine.core.flows.events.Signal",
                        "io.canopy.engine.core.flows.events.Computed",
                        "io.canopy.engine.core.flows.events.Effect",
                        "io.canopy.engine.core.flows.events.NoArgEvent",
                        "io.canopy.engine.core.flows.events.OneArgEvent",
                        "io.canopy.engine.core.flows.events.TwoArgsEvent"
                    )
                ) {
                    return expression
                }
                return protect(expression)
            }

            private fun protect(expression: IrFunctionAccessExpression): IrExpression {
                val caller = owner ?: return expression
                val currentReceiver = receiver ?: return expression
                if (beforeConstruction) return expression
                // Explicit ownership, including null for shared lifetime, is always authoritative.
                val parameters = expression.symbol.owner.parameters
                val explicit = parameters.indexOfFirst { it.name.asString() == "owner" }
                if (explicit >= 0 && expression.arguments[explicit] != null) return expression
                if (boundary == null) {
                    checks.report(
                        "CANOPY_OWNERSHIP_ABI",
                        caller,
                        "Update the runtime and compiler together: missing compilerOwned"
                    )
                    return expression
                }
                val scope = parent ?: caller
                val function = pluginContext.irFactory.buildFun {
                    name = Name.special("<anonymous>")
                    returnType = expression.type
                    visibility = DescriptorVisibilities.LOCAL
                    origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                    startOffset = expression.startOffset
                    endOffset = expression.endOffset
                }.apply {
                    parent = scope
                    body = DeclarationIrBuilder(pluginContext, symbol, startOffset, endOffset).irBlockBody {
                        +irReturn(expression)
                    }
                }
                val builder =
                    DeclarationIrBuilder(pluginContext, function.symbol, expression.startOffset, expression.endOffset)
                return builder.irBlock(resultType = expression.type) {
                    // Evaluate arguments first: suspension and nonlocal returns must not span a thread-local scope.
                    expression.arguments.indices.forEach { index ->
                        val argument = expression.arguments[index]
                        if (argument != null) expression.arguments[index] = builder.irGet(irTemporary(argument))
                    }
                    +builder.irCall(boundary.symbol, expression.type).apply {
                        dispatchReceiver = builder.irGet(currentReceiver)
                        typeArguments[0] = expression.type
                        arguments[1] = IrFunctionExpressionImpl(
                            expression.startOffset,
                            expression.endOffset,
                            pluginContext.irBuiltIns.functionN(0).typeWith(expression.type),
                            function,
                            IrStatementOrigin.LAMBDA
                        )
                    }
                }.also { it.patchDeclarationParents(scope) }
            }
        })
    }
}
