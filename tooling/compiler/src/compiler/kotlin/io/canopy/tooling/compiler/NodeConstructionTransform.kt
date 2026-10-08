@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.canopy.tooling.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Internal construction lowering; public rule providers remain validation-only. */
internal class NodeConstructionTransform(private val messages: MessageCollector) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val checks = CanopyRuleContext(moduleFragment, pluginContext, messages)
        val boundaries = pluginContext.referenceFunctions(
            CallableId(FqName("io.canopy.engine.core.nodes"), Name.identifier("nodeConstruction"))
        )
        val boundary = boundaries.singleOrNull { symbol ->
            val function = symbol.owner
            function.typeParameters.size == 1 &&
                function.parameters.size == 1 &&
                function.parameters.single().kind == IrParameterKind.Regular &&
                function.parameters.single().type.classFqName?.asString() == "kotlin.Function0" &&
                (function.parameters.single().type as? IrSimpleType)?.arguments?.singleOrNull()?.typeOrNull ==
                function.typeParameters.single().defaultType &&
                function.returnType == function.typeParameters.single().defaultType &&
                function.visibility == DescriptorVisibilities.PUBLIC &&
                function.isInline &&
                !function.isSuspend
        }
        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {
            private lateinit var file: IrFile
            private var parent: IrDeclarationParent? = null

            override fun visitFile(declaration: IrFile): IrFile {
                file = declaration
                return super.visitFile(declaration)
            }

            override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
                val previous = parent
                if (declaration is IrDeclarationParent) parent = declaration
                val result = super.visitDeclaration(declaration)
                parent = previous
                return result
            }

            private fun report(expression: IrExpression, id: String, explanation: String) {
                val offset = expression.startOffset.coerceAtLeast(0)
                messages.report(
                    CompilerMessageSeverity.ERROR,
                    "$id: $explanation",
                    CompilerMessageLocation.create(
                        file.fileEntry.name,
                        file.fileEntry.getLineNumber(offset) + 1,
                        file.fileEntry.getColumnNumber(offset) + 1,
                        null
                    )
                )
            }

            private fun suspendingEvaluation(expression: IrElement): IrCall? {
                var suspended: IrCall? = null
                expression.accept(
                    object : IrVisitorVoid() {
                        override fun visitElement(element: IrElement) {
                            if (suspended == null) element.acceptChildrenVoid(this)
                        }
                        override fun visitFunctionExpression(expression: IrFunctionExpression) = Unit
                        override fun visitDeclaration(declaration: IrDeclarationBase) = Unit
                        override fun visitVariable(declaration: IrVariable) {
                            declaration.initializer?.accept(this, null)
                        }
                        override fun visitCall(expression: IrCall) {
                            if (expression.symbol.owner.isSuspend) suspended = expression
                            if (suspended != null) return
                            if (expression.symbol.owner.isInline) {
                                expression.arguments.forEachIndexed { index, argument ->
                                    if (argument is IrFunctionExpression &&
                                        !expression.symbol.owner.parameters[index].isNoinline
                                    ) {
                                        argument.function.body?.accept(this, null)
                                    }
                                }
                            }
                            expression.acceptChildrenVoid(this)
                        }
                    },
                    null
                )
                return suspended
            }

            override fun visitCall(expression: IrCall): IrExpression {
                expression.transformChildrenVoid(this)
                if (expression.symbol.owner.fqNameWhenAvailable?.asString() ==
                    "io.canopy.engine.core.nodes.nodeConstruction"
                ) {
                    val body = (expression.arguments.singleOrNull() as? IrFunctionExpression)?.function?.body
                    if (body != null) suspendingEvaluation(body)?.let { reportSuspension(it) }
                }
                return expression
            }

            private fun reportSuspension(expression: IrExpression) {
                report(
                    expression,
                    "CANOPY_NODE_CONSTRUCTION_SUSPEND",
                    "Node construction is synchronous and cannot span suspension; " +
                        "evaluate suspend arguments before calling the constructor or nodeConstruction"
                )
            }

            override fun visitFunctionReference(expression: IrFunctionReference): IrExpression {
                expression.transformChildrenVoid(this)
                val constructor = expression.symbol.owner as? IrConstructor ?: return expression
                if (checks.isNodeSubclass(constructor.parentAsClass)) {
                    report(
                        expression,
                        "CANOPY_NODE_CONSTRUCTION_REFERENCE",
                        "Node constructor references cannot protect failed initialization; use an explicit lambda " +
                            "calling the constructor, or nodeConstruction around an external factory invocation"
                    )
                }
                return expression
            }

            private val objectLiteralCalls = mutableSetOf<IrConstructorCall>()

            override fun visitBlock(expression: IrBlock): IrExpression {
                val call = expression.statements.lastOrNull() as? IrConstructorCall
                if (expression.origin != IrStatementOrigin.OBJECT_LITERAL ||
                    call == null ||
                    !checks.isNodeSubclass(call.symbol.owner.parentAsClass)
                ) {
                    return super.visitBlock(expression)
                }
                // JVM lowering requires the entire object-literal block to end in its original constructor call.
                objectLiteralCalls += call
                try {
                    expression.transformChildrenVoid(this)
                } finally {
                    objectLiteralCalls -= call
                }
                return protect(expression)
            }

            override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
                // Transform existing children first, then introduce a lambda without visiting the original call again.
                expression.transformChildrenVoid(this)
                if (expression in objectLiteralCalls ||
                    !checks.isNodeSubclass(expression.symbol.owner.parentAsClass)
                ) {
                    return expression
                }
                return protect(expression)
            }

            private fun protect(expression: IrExpression): IrExpression {
                suspendingEvaluation(expression)?.let { suspendCall ->
                    reportSuspension(suspendCall)
                    return expression
                }
                if (boundary == null) {
                    report(
                        expression,
                        "CANOPY_NODE_CONSTRUCTION_ABI",
                        "Node construction requires the matching inline runtime nodeConstruction<T>(() -> T); " +
                            "update the runtime and compiler together"
                    )
                    return expression
                }
                val owner = parent ?: file
                val function = pluginContext.irFactory.buildFun {
                    name = Name.special("<anonymous>")
                    returnType = expression.type
                    visibility = DescriptorVisibilities.LOCAL
                    origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                    startOffset = expression.startOffset
                    endOffset = expression.endOffset
                }.apply {
                    parent = owner
                    body = DeclarationIrBuilder(pluginContext, symbol, startOffset, endOffset).irBlockBody {
                        +irReturn(expression)
                    }
                    patchDeclarationParents(owner)
                }
                val builder = DeclarationIrBuilder(
                    pluginContext,
                    function.symbol,
                    expression.startOffset,
                    expression.endOffset
                )
                return builder.irCall(boundary, expression.type).apply {
                    typeArguments[0] = expression.type
                    arguments[0] = IrFunctionExpressionImpl(
                        expression.startOffset,
                        expression.endOffset,
                        pluginContext.irBuiltIns.functionN(0).typeWith(expression.type),
                        function,
                        IrStatementOrigin.LAMBDA
                    )
                }
            }
        })
    }
}
