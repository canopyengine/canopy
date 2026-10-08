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
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDoWhileLoop
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

/** Captures expressions only at the resolved declarative UI ABI; validators remain read-only. */
internal class UiExpressionTransform(private val messages: MessageCollector) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildren(
            object : IrElementTransformerVoid() {
                private var function: IrSimpleFunction? = null
                private val enclosingFunctions = mutableListOf<IrSimpleFunction>()
                private val structuralLocals = mutableSetOf<IrVariable>()
                override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
                    val previous = function
                    if (previous != null) enclosingFunctions += previous
                    function = declaration
                    try {
                        if (declaration.name.asString() != "<anonymous>" &&
                            uiReceiver() != null &&
                            declaration.returnType != pluginContext.irBuiltIns.unitType &&
                            declaresUi(declaration)
                        ) {
                            diagnostic(
                                declaration,
                                "Reusable UI declaration functions must return Unit. " +
                                    "Declare elements inside the component instead of returning a conditional element."
                            )
                            return declaration
                        }
                        return super.visitSimpleFunction(declaration)
                    } finally {
                        function = previous
                        if (previous != null) enclosingFunctions.removeAt(enclosingFunctions.lastIndex)
                    }
                }

                private fun diagnostic(element: IrElement, explanation: String) {
                    val file = function!!.file
                    val offset = element.startOffset.coerceAtLeast(0)
                    messages.report(
                        CompilerMessageSeverity.ERROR,
                        "CANOPY_UI_UNSUPPORTED: $explanation",
                        CompilerMessageLocation.create(
                            file.fileEntry.name,
                            file.fileEntry.getLineNumber(offset) + 1,
                            file.fileEntry.getColumnNumber(offset) + 1,
                            null
                        )
                    )
                }

                override fun visitWhileLoop(loop: IrWhileLoop): IrExpression {
                    if (uiReceiver() != null &&
                        declaresUi(loop) &&
                        loop.origin != IrStatementOrigin.FOR_LOOP_INNER_WHILE
                    ) {
                        diagnostic(
                            loop,
                            "Declarative repeated children require a for loop with explicit key(item) scopes."
                        )
                    }
                    return super.visitWhileLoop(loop)
                }

                override fun visitDoWhileLoop(loop: IrDoWhileLoop): IrExpression {
                    if (uiReceiver() != null && declaresUi(loop)) {
                        diagnostic(
                            loop,
                            "Declarative repeated children require a for loop with explicit key(item) scopes."
                        )
                    }
                    return super.visitDoWhileLoop(loop)
                }

                private fun uiReceiver(): org.jetbrains.kotlin.ir.declarations.IrValueParameter? {
                    val current = function ?: return null
                    if (current.file.packageFqName.asString() == "io.canopy.engine.ui") return null
                    current.parameters.firstOrNull {
                        it.type.classFqName?.asString() == "io.canopy.engine.ui.UiScope"
                    }?.let { return it }
                    // UI element configuration lambdas retain the surrounding declaration scope;
                    // zero-argument action callbacks deliberately do not inherit it.
                    if (current.parameters.none {
                            it.type.classFqName?.asString() == "io.canopy.engine.ui.UiElement"
                        }
                    ) {
                        return null
                    }
                    return enclosingFunctions.asReversed().firstNotNullOfOrNull { enclosing ->
                        enclosing.parameters.firstOrNull {
                            it.type.classFqName?.asString() == "io.canopy.engine.ui.UiScope"
                        }
                    }
                }

                private fun declaresUi(element: IrElement): Boolean {
                    var found = false
                    element.acceptChildrenVoid(object : IrVisitorVoid() {
                        override fun visitElement(element: IrElement) {
                            element.acceptChildrenVoid(this)
                        }
                        override fun visitCall(expression: IrCall) {
                            val parent = expression.symbol.owner.parent as? IrClass
                            if (parent?.fqNameWhenAvailable?.asString() == "io.canopy.engine.ui.UiScope" ||
                                expression.symbol.owner.parameters.any {
                                    it.type.classFqName?.asString() == "io.canopy.engine.ui.UiScope"
                                }
                            ) {
                                found = true
                            }
                            expression.acceptChildrenVoid(this)
                        }
                    })
                    return found
                }

                override fun visitWhen(expression: IrWhen): IrExpression {
                    if (uiReceiver() != null && declaresUi(expression)) {
                        return structural(expression)
                    }
                    return super.visitWhen(expression)
                }

                override fun visitBlock(expression: IrBlock): IrExpression {
                    if (expression.origin == IrStatementOrigin.FOR_LOOP &&
                        uiReceiver() != null &&
                        declaresUi(expression)
                    ) {
                        var keyed = false
                        var unkeyedDeclaration = false
                        expression.acceptChildrenVoid(object : IrVisitorVoid() {
                            override fun visitElement(element: IrElement) {
                                element.acceptChildrenVoid(this)
                            }
                            override fun visitCall(call: IrCall) {
                                val target = call.symbol.owner
                                if (target.name.asString() == "key" &&
                                    (target.parent as? IrClass)?.fqNameWhenAvailable?.asString() ==
                                    "io.canopy.engine.ui.UiScope"
                                ) {
                                    keyed = true
                                    // Children inside this explicit item scope are already protected.
                                    return
                                }
                                if ((target.parent as? IrClass)?.fqNameWhenAvailable?.asString() ==
                                    "io.canopy.engine.ui.UiScope" ||
                                    target.parameters.any {
                                        it.type.classFqName?.asString() == "io.canopy.engine.ui.UiScope"
                                    }
                                ) {
                                    unkeyedDeclaration = true
                                }
                                call.acceptChildrenVoid(this)
                            }
                            override fun visitBlock(block: IrBlock) {
                                if (block !== expression &&
                                    block.origin == IrStatementOrigin.FOR_LOOP &&
                                    declaresUi(block)
                                ) {
                                    unkeyedDeclaration = true
                                    return
                                }
                                block.acceptChildrenVoid(this)
                            }
                        })
                        if (!keyed || unkeyedDeclaration) {
                            diagnostic(
                                expression,
                                "Each declarative for-loop item requires an explicit key(item) scope. " +
                                    "Place every child declaration inside that scope."
                            )
                            return super.visitBlock(expression)
                        }
                        return structural(expression)
                    }
                    return super.visitBlock(expression)
                }

                private fun structural(expression: IrExpression): IrExpression {
                    val owner = function!!
                    val receiver = uiReceiver()!!
                    expression.acceptChildrenVoid(object : IrVisitorVoid() {
                        override fun visitElement(element: IrElement) {
                            element.acceptChildrenVoid(this)
                        }
                        override fun visitVariable(declaration: IrVariable) {
                            structuralLocals += declaration
                            declaration.acceptChildrenVoid(this)
                        }
                    })
                    var invalidReturn = false
                    expression.acceptChildrenVoid(object : IrVisitorVoid() {
                        override fun visitElement(element: IrElement) {
                            element.acceptChildrenVoid(this)
                        }
                        override fun visitFunctionExpression(expression: IrFunctionExpression) = Unit
                        override fun visitReturn(expression: IrReturn) {
                            if (expression.returnTargetSymbol == owner.symbol) invalidReturn = true
                        }
                    })
                    if (invalidReturn) {
                        diagnostic(expression, "Non-local returns cannot cross a reactive structural region.")
                        return expression
                    }
                    val scope = receiver.type.classifierOrNull!!.owner as IrClass
                    val hook = scope.declarations.filterIsInstance<IrSimpleFunction>().single {
                        it.name.asString() == "structure"
                    }
                    val content = pluginContext.irFactory.buildFun {
                        name = org.jetbrains.kotlin.name.Name.special("<anonymous>")
                        returnType = pluginContext.irBuiltIns.unitType
                        visibility = DescriptorVisibilities.LOCAL
                        origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                        startOffset = expression.startOffset
                        endOffset = expression.endOffset
                    }.apply { parent = owner }
                    val nestedReceiver = content.addValueParameter("scope", receiver.type)
                    expression.transformChildren(
                        object : IrElementTransformerVoid() {
                            override fun visitGetValue(expression: IrGetValue): IrExpression =
                                if (expression.symbol == receiver.symbol) {
                                    DeclarationIrBuilder(pluginContext, content.symbol).irGet(nestedReceiver)
                                } else {
                                    expression
                                }
                        },
                        null
                    )
                    val previous = function
                    function = content
                    try {
                        expression.transformChildren(this, null)
                    } finally {
                        function = previous
                    }
                    content.body = DeclarationIrBuilder(pluginContext, content.symbol).irBlockBody { +expression }
                    content.patchDeclarationParents(owner)
                    val evaluator = IrFunctionExpressionImpl(
                        expression.startOffset,
                        expression.endOffset,
                        hook.parameters[2].type,
                        content,
                        IrStatementOrigin.LAMBDA
                    )
                    val builder =
                        DeclarationIrBuilder(pluginContext, owner.symbol, expression.startOffset, expression.endOffset)
                    return builder.irCall(hook.symbol).apply {
                        arguments[0] = builder.irGet(receiver)
                        arguments[1] = builder.irString("${owner.file.fileEntry.name}:${expression.startOffset}")
                        arguments[2] = evaluator
                    }
                }

                override fun visitVariable(declaration: IrVariable): IrStatement {
                    if (uiReceiver() != null &&
                        declaration.initializer is IrWhen &&
                        declaresUi(declaration.initializer!!)
                    ) {
                        diagnostic(
                            declaration,
                            "A conditional declaring UI children must be a statement, " +
                                "not a value assigned as UiElement."
                        )
                        return declaration
                    }
                    declaration.transformChildren(this, null)
                    val receiver = uiReceiver() ?: return declaration
                    val owner = function ?: return declaration
                    val initial = declaration.initializer ?: return declaration
                    var ownsReactiveState = false
                    initial.acceptVoid(object : IrVisitorVoid() {
                        override fun visitElement(element: IrElement) {
                            element.acceptChildrenVoid(this)
                        }
                        override fun visitCall(call: IrCall) {
                            val fqName = call.symbol.owner.fqNameWhenAvailable?.asString().orEmpty()
                            if (fqName.startsWith("io.canopy.engine.core.flows.events.") &&
                                call.symbol.owner.name.asString() in setOf("signal", "computed", "effect")
                            ) {
                                ownsReactiveState = true
                            }
                            call.acceptChildrenVoid(this)
                        }
                    })
                    val constructedClass = (initial as? IrConstructorCall)?.symbol?.owner?.parent as? IrClass
                    val isComponent = constructedClass?.declarations?.filterIsInstance<IrSimpleFunction>()?.any {
                        it.parameters.any { parameter ->
                            parameter.type.classFqName?.asString() == "io.canopy.engine.ui.UiScope"
                        }
                    } == true
                    if (!ownsReactiveState && !isComponent) return declaration
                    val scope = receiver.type.classifierOrNull!!.owner as IrClass
                    val hook = scope.declarations.filterIsInstance<IrSimpleFunction>().single {
                        it.name.asString() == "remember"
                    }
                    val evaluatorType = pluginContext.irBuiltIns.functionN(0).typeWith(declaration.type)
                    val evaluator = capture(initial, evaluatorType, owner, pluginContext)
                    val builder =
                        DeclarationIrBuilder(pluginContext, owner.symbol, initial.startOffset, initial.endOffset)
                    declaration.initializer = builder.irCall(hook.symbol).apply {
                        type = declaration.type
                        typeArguments[0] = declaration.type
                        arguments[0] = builder.irGet(receiver)
                        arguments[1] = builder.irString("${owner.file.fileEntry.name}:${declaration.startOffset}:state")
                        arguments[2] = evaluator
                    }
                    return declaration
                }

                override fun visitCall(expression: IrCall): IrExpression {
                    expression.transformChildren(this, null)
                    val target = expression.symbol.owner
                    val receiver = uiReceiver()
                    val owner = function ?: return expression
                    if (receiver != null &&
                        target.parameters.any {
                            it.type.classFqName?.asString() == "io.canopy.engine.ui.UiScope"
                        } &&
                        (target.parent as? IrClass)?.fqNameWhenAvailable?.asString() !=
                        "io.canopy.engine.ui.UiScope" &&
                        target.returnType == pluginContext.irBuiltIns.unitType
                    ) {
                        return structural(expression)
                    }
                    val targetClass = target.parent as? IrClass ?: return expression
                    val isUiElement = targetClass.fqNameWhenAvailable?.asString() == "io.canopy.engine.ui.UiElement" ||
                        expression.arguments.getOrNull(0)?.type?.classFqName?.asString() ==
                        "io.canopy.engine.ui.UiElement"
                    if (receiver != null && isUiElement) {
                        val hookName = when (target.name.asString()) {
                            "<set-style>" -> "bindStyle"
                            "<set-isVisible>" -> "bindVisible"
                            "<set-enabled>" -> "bindEnabled"
                            else -> null
                        }
                        if (hookName != null) {
                            val scope = receiver.type.classifierOrNull!!.owner as IrClass
                            val hook = scope.declarations.filterIsInstance<IrSimpleFunction>().single {
                                it.name.asString() == hookName
                            }
                            val builder =
                                DeclarationIrBuilder(
                                    pluginContext,
                                    owner.symbol,
                                    expression.startOffset,
                                    expression.endOffset
                                )
                            val value = expression.arguments.getOrNull(1) ?: return expression
                            return builder.irCall(hook.symbol).apply {
                                arguments[0] = builder.irGet(receiver)
                                arguments[1] =
                                    builder.irString("${owner.file.fileEntry.name}:${expression.startOffset}:property")
                                arguments[2] = expression.arguments.getOrNull(0)
                                arguments[3] = capture(value, hook.parameters[3].type, owner, pluginContext)
                            }
                        }
                    }
                    val scope = targetClass
                    if (scope.fqNameWhenAvailable?.asString() != "io.canopy.engine.ui.UiScope") return expression
                    val name = when (target.name.asString()) {
                        "Text" -> "bindText"
                        "Button" -> "bindButtonText"
                        "Row" -> "bindRow"
                        "Column" -> "bindColumn"
                        "Box" -> "bindBox"
                        else -> return expression
                    }
                    if (uiReceiver() == null) return expression
                    if (owner.file.packageFqName.asString() == "io.canopy.engine.ui") return expression
                    if (expression.arguments.getOrNull(1) == null) return expression
                    val hook = scope.declarations.filterIsInstance<IrSimpleFunction>().single {
                        it.name.asString() ==
                            name
                    }
                    val builder =
                        DeclarationIrBuilder(pluginContext, owner.symbol, expression.startOffset, expression.endOffset)
                    val value = expression.arguments.getOrNull(1) ?: return expression
                    value.acceptVoid(object : IrVisitorVoid() {
                        override fun visitElement(element: IrElement) {
                            element.acceptChildrenVoid(this)
                        }
                        override fun visitGetValue(expression: IrGetValue) {
                            val local = expression.symbol.owner as? IrVariable ?: return
                            if (!local.isVar &&
                                local.origin == IrDeclarationOrigin.DEFINED &&
                                local !in structuralLocals &&
                                local.type.classFqName?.asString() == "kotlin.String" &&
                                local.initializer !is IrConst
                            ) {
                                diagnostic(
                                    expression,
                                    "A derived local string is initialized once. Put its expression directly " +
                                        "in Text/Button, or use computed and read it in the property expression."
                                )
                            }
                        }
                    })
                    val evaluator = capture(value, hook.parameters[2].type, owner, pluginContext)
                    return builder.irCall(hook.symbol).apply {
                        arguments[0] = expression.arguments.getOrNull(0)
                        arguments[1] = builder.irString("${owner.file.fileEntry.name}:${expression.startOffset}")
                        arguments[2] = evaluator
                        if (name != "bindText") arguments[3] = expression.arguments.getOrNull(2)
                    }
                }
            },
            null
        )
    }

    private fun capture(
        expression: IrExpression,
        type: IrType,
        parent: IrSimpleFunction,
        context: IrPluginContext,
    ): IrExpression {
        val function = context.irFactory.buildFun {
            name = org.jetbrains.kotlin.name.Name.special("<anonymous>")
            returnType = expression.type
            visibility = DescriptorVisibilities.LOCAL
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            startOffset = expression.startOffset
            endOffset = expression.endOffset
        }.apply {
            this.parent = parent
            body = DeclarationIrBuilder(context, symbol, startOffset, endOffset).irBlockBody { +irReturn(expression) }
            patchDeclarationParents(parent)
        }
        return IrFunctionExpressionImpl(
            expression.startOffset,
            expression.endOffset,
            type,
            function,
            IrStatementOrigin.LAMBDA
        )
    }
}
