@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.canopy.tooling.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Validates constructor inputs before storage lowering obscures their primary-constructor properties. */
internal class NodeInputDeclarationTransform(private val messages: DiagnosticMessages) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val checks = CanopyRuleContext(moduleFragment, pluginContext, messages)
        moduleFragment.acceptChildrenVoid(object : IrVisitorVoid() {
            private lateinit var file: IrFile

            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitFile(declaration: IrFile) {
                file = declaration
                declaration.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                val constructors = declaration.declarations.filterIsInstance<IrConstructor>()
                val primary = constructors.singleOrNull { it.isPrimary }
                val inputs = primary?.parameters?.filter { it.hasAnnotation(inputAnnotation) }.orEmpty()
                if (inputs.isNotEmpty()) {
                    constructors.filterNot { it.isPrimary }.forEach {
                        messages.emit(
                            "CANOPY_INPUT_UNSUPPORTED_CONSTRUCTION",
                            "Secondary constructors cannot forward reactive inputs",
                            file,
                            it,
                            help = "Use the primary constructor directly; secondary delegation cannot retain " +
                                "the caller expression."
                        )
                    }
                }
                declaration.acceptChildrenVoid(this)
            }

            override fun visitValueParameter(declaration: IrValueParameter) {
                if (declaration.hasAnnotation(inputAnnotation)) {
                    val constructor = declaration.parent as? IrConstructor
                    val owner = constructor?.parent as? IrClass
                    val property = owner?.declarations?.filterIsInstance<IrProperty>()
                        ?.singleOrNull { it.name == declaration.name }
                    val (code, title, help) = when {
                        constructor == null ||
                            !constructor.isPrimary ||
                            property == null ||
                            property.startOffset !in declaration.startOffset..declaration.endOffset -> Triple(
                            "CANOPY_INPUT_INVALID_TARGET",
                            "Inputs must be primary-constructor properties",
                            "Declare the input as @Input val name: Type in a Node's primary constructor."
                        )
                        !checks.isNodeSubclass(owner) || owner.isInner -> Triple(
                            "CANOPY_INPUT_UNSUPPORTED_OWNER",
                            "Inputs require a supported Node owner",
                            "Declare the input on a non-inner Canopy Node subclass."
                        )
                        property.isVar -> Triple(
                            "CANOPY_INPUT_MUST_BE_VAL",
                            "Inputs must be read-only val properties",
                            "Replace var with val and report user changes through callbacks."
                        )
                        property.backingField == null ||
                            property.isDelegated ||
                            property.unsupportedNodeStorage() != null -> Triple(
                            "CANOPY_INPUT_INVALID_LOWERING",
                            "This input cannot use guarded Node storage",
                            "Use a supported primary-constructor val property without field annotations."
                        )
                        declaration.defaultValue?.expression?.let { it !is IrConst } == true -> Triple(
                            "CANOPY_INPUT_UNSUPPORTED_CONSTRUCTION",
                            "Reactive input defaults are not supported",
                            "Use a literal default, or pass the expression explicitly at the call site."
                        )
                        else -> null
                    } ?: return declaration.acceptChildrenVoid(this)
                    messages.emit(code, title, file, declaration, help = help)
                }
                declaration.acceptChildrenVoid(this)
            }
        })
    }
}

/** Captures explicit input expressions and installs them after successful Node construction. */
internal class NodeInputCallTransform(private val messages: DiagnosticMessages) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val checks = CanopyRuleContext(moduleFragment, pluginContext, messages)
        val runtime = pluginContext.referenceClass(
            ClassId.topLevel(FqName("io.canopy.engine.core.flows.events.InputRuntime"))
        )?.owner
        val captureClass = pluginContext.referenceClass(
            ClassId.topLevel(FqName("io.canopy.engine.core.flows.events.InputCapture"))
        )?.owner
        val capture = runtime?.functions?.singleOrNull {
            it.name.asString() == "capture" && it.typeParameters.size == 1
        }
        val install = runtime?.functions?.singleOrNull {
            it.name.asString() == "install" && it.typeParameters.size == 1
        }
        val initialValue = captureClass?.properties?.singleOrNull { it.name.asString() == "initialValue" }?.getter
        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {
            private lateinit var file: IrFile
            private var parent: IrDeclarationParent? = null
            private val preparedCalls = mutableMapOf<IrConstructorCall, Map<Int, IrVariable>>()
            private val rejectedCalls = mutableSetOf<IrConstructorCall>()

            private fun unsafeCapture(argument: IrExpression): Pair<IrExpression, String>? {
                var unsafe: Pair<IrExpression, String>? = null
                val visitor = object : IrVisitorVoid() {
                    override fun visitElement(element: IrElement) {
                        if (unsafe == null) element.acceptChildrenVoid(this)
                    }

                    override fun visitFunctionExpression(expression: IrFunctionExpression) = Unit

                    override fun visitDeclaration(declaration: IrDeclarationBase) = Unit

                    override fun visitVariable(declaration: IrVariable) {
                        declaration.initializer?.accept(this, null)
                    }

                    override fun visitReturn(expression: IrReturn) {
                        unsafe = expression to "A return during input evaluation cannot be retained for reevaluation."
                    }

                    override fun visitCall(expression: IrCall) {
                        if (expression.symbol.owner.isSuspend) {
                            unsafe = expression to "A suspending call cannot run in a synchronous reactive input."
                            return
                        }
                        if (expression.symbol.owner.isInline) {
                            expression.arguments.forEachIndexed { index, nested ->
                                if (nested is IrFunctionExpression &&
                                    !expression.symbol.owner.parameters[index].isNoinline
                                ) {
                                    nested.function.body?.accept(this, null)
                                }
                            }
                        }
                        expression.acceptChildrenVoid(this)
                    }
                }
                argument.accept(visitor, null)
                return unsafe
            }

            private fun rejectCapture(unsafe: Pair<IrExpression, String>) {
                messages.emit(
                    "CANOPY_INPUT_UNSUPPORTED_CONSTRUCTION",
                    "This input expression cannot be reevaluated",
                    file,
                    unsafe.first,
                    label = unsafe.second,
                    help = "Evaluate it before construction, or pass a synchronous expression " +
                        "without immediately executed returns."
                )
            }

            private fun captureArgument(
                argument: IrExpression,
                builder: DeclarationIrBuilder,
                owner: IrDeclarationParent,
            ): IrExpression {
                val runtimeClass = requireNotNull(runtime)
                val captureClassType = requireNotNull(captureClass)
                val captureFunction = requireNotNull(capture)
                val lambda = pluginContext.irFactory.buildFun {
                    name = Name.special("<anonymous>")
                    returnType = argument.type
                    visibility = DescriptorVisibilities.LOCAL
                    origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                    startOffset = argument.startOffset
                    endOffset = argument.endOffset
                }.apply {
                    parent = owner
                    body = DeclarationIrBuilder(pluginContext, symbol, startOffset, endOffset).irBlockBody {
                        +irReturn(argument)
                    }
                    patchDeclarationParents(owner)
                }
                return builder.irCall(captureFunction.symbol, captureClassType.typeWith(argument.type)).apply {
                    dispatchReceiver = builder.irGetObject(runtimeClass.symbol)
                    typeArguments[0] = argument.type
                    arguments[1] = IrFunctionExpressionImpl(
                        argument.startOffset,
                        argument.endOffset,
                        pluginContext.irBuiltIns.functionN(0).typeWith(argument.type),
                        lambda,
                        IrStatementOrigin.LAMBDA
                    )
                }
            }

            private fun capturedInitial(
                captured: IrVariable,
                type: IrType,
                builder: DeclarationIrBuilder,
            ): IrExpression = builder.irCall(requireNotNull(initialValue).symbol, type).apply {
                dispatchReceiver = builder.irGet(captured)
            }

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

            override fun visitBlock(expression: IrBlock): IrExpression {
                if (expression.origin != IrStatementOrigin.ARGUMENTS_REORDERING_FOR_CALL) {
                    return super.visitBlock(expression)
                }
                val call = expression.statements.lastOrNull() as? IrConstructorCall
                    ?: return super.visitBlock(expression)
                val constructor = call.symbol.owner
                val inputsByVariable = constructor.parameters.withIndex().mapNotNull { (index, parameter) ->
                    if (!parameter.hasAnnotation(inputAnnotation)) return@mapNotNull null
                    val variable = (call.arguments[index] as? IrGetValue)?.symbol?.owner as? IrVariable
                        ?: return@mapNotNull null
                    if (variable !in expression.statements || variable.initializer == null) return@mapNotNull null
                    variable to index
                }.toMap()
                if (inputsByVariable.isEmpty()) return super.visitBlock(expression)
                val unsafe = inputsByVariable.keys.mapNotNull { it.initializer?.let(::unsafeCapture) }.firstOrNull()
                if (unsafe != null) {
                    rejectCapture(unsafe)
                    rejectedCalls += call
                    return try {
                        super.visitBlock(expression)
                    } finally {
                        rejectedCalls -= call
                    }
                }
                val owner = parent ?: file
                val builder = DeclarationIrBuilder(
                    pluginContext,
                    constructor.symbol,
                    expression.startOffset,
                    expression.endOffset
                )
                val transformer = this
                val captures = mutableMapOf<Int, IrVariable>()
                return builder.irBlock(resultType = expression.type, origin = expression.origin) {
                    expression.statements.dropLast(1).forEach { statement ->
                        if (statement is IrVariable) {
                            val index = inputsByVariable[statement]
                            if (index != null) {
                                val original = statement.initializer!!.transform(transformer, null)
                                val captured = irTemporary(captureArgument(original, builder, owner))
                                captures[index] = captured
                                statement.initializer = capturedInitial(captured, original.type, builder)
                            }
                        }
                        +(statement.transform(transformer, null) as IrStatement)
                    }
                    preparedCalls[call] = captures.toMap()
                    try {
                        +(call.transform(transformer, null) as IrStatement)
                    } finally {
                        preparedCalls.remove(call)
                    }
                }.also { it.patchDeclarationParents(owner) }
            }

            override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall): IrExpression {
                expression.transformChildrenVoid(this)
                val uncaptured = expression.symbol.owner.parameters.withIndex().any { (index, parameter) ->
                    parameter.hasAnnotation(inputAnnotation) &&
                        expression.arguments[index]?.let { it !is IrConst } == true
                }
                if (uncaptured) {
                    messages.emit(
                        "CANOPY_INPUT_UNSUPPORTED_CONSTRUCTION",
                        "Constructor delegation cannot capture a dynamic input",
                        file,
                        expression,
                        help = "Use a literal super input or pass the expression through " +
                            "a direct primary-constructor call."
                    )
                }
                return expression
            }

            override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
                if (expression in rejectedCalls) {
                    expression.transformChildrenVoid(this)
                    return expression
                }
                val constructor = expression.symbol.owner
                val unsafe = constructor.parameters.withIndex().mapNotNull { (index, parameter) ->
                    if (!parameter.hasAnnotation(inputAnnotation)) return@mapNotNull null
                    expression.arguments[index]?.let(::unsafeCapture)
                }.firstOrNull()
                if (unsafe != null) {
                    rejectCapture(unsafe)
                    expression.transformChildrenVoid(this)
                    return expression
                }
                expression.transformChildrenVoid(this)
                val prepared = preparedCalls[expression].orEmpty()
                val inputs = constructor.parameters.withIndex().filter {
                    it.value.kind == IrParameterKind.Regular && it.value.hasAnnotation(inputAnnotation)
                }
                if (inputs.isEmpty()) return expression
                if (inputs.none { expression.arguments[it.index] != null }) return expression
                if (!checks.isNodeSubclass(constructor.parentAsClass) || !constructor.isPrimary) {
                    messages.emit(
                        "CANOPY_INPUT_UNSUPPORTED_CONSTRUCTION",
                        "This constructor does not support reactive inputs",
                        file,
                        expression,
                        help = "Call the annotated Node primary constructor directly."
                    )
                    return expression
                }
                if (runtime == null ||
                    captureClass == null ||
                    capture == null ||
                    install == null ||
                    initialValue == null
                ) {
                    messages.emit(
                        "CANOPY_INPUT_ABI",
                        "Engine and compiler builds are incompatible",
                        label = "The resolved engine lacks the reactive input runtime ABI.",
                        help = "Publish the engine and compiler from the same checkout, then rebuild."
                    )
                    return expression
                }
                val owner = parent ?: file
                val builder = DeclarationIrBuilder(
                    pluginContext,
                    constructor.symbol,
                    expression.startOffset,
                    expression.endOffset
                )
                val captured = mutableListOf<Triple<IrVariable, String, IrType>>()
                val inputIndices = inputs.map { it.index }.toSet()
                return builder.irBlock(resultType = expression.type, origin = canopyInputCallOrigin) {
                    expression.arguments.indices.forEach { index ->
                        val argument = expression.arguments[index] ?: return@forEach
                        if (index !in inputIndices) {
                            expression.arguments[index] = builder.irGet(irTemporary(argument))
                            return@forEach
                        }
                        val capturedValue = prepared[index] ?: irTemporary(captureArgument(argument, builder, owner))
                        expression.arguments[index] = if (index in prepared) {
                            builder.irGet(irTemporary(argument))
                        } else {
                            capturedInitial(capturedValue, argument.type, builder)
                        }
                        val property = constructor.parentAsClass.properties.singleOrNull {
                            it.name == constructor.parameters[index].name
                        }
                        if (property == null || property.backingField != null) {
                            messages.emit(
                                "CANOPY_INPUT_INVALID_LOWERING",
                                "Input declaration lacks compiler-lowered storage",
                                file,
                                expression,
                                help = "Apply the Canopy compiler to the declaring module and recompile it."
                            )
                        } else {
                            val ownerKey = constructor.parentAsClass.fqNameWhenAvailable?.asString()
                                ?: "${constructor.parentAsClass.file.fileEntry.name}:" +
                                "${constructor.parentAsClass.startOffset}:${constructor.parentAsClass.name}"
                            val name = property.name.asString()
                            captured += Triple(
                                capturedValue,
                                "${ownerKey.length}:$ownerKey${name.length}:$name",
                                argument.type
                            )
                        }
                    }
                    val node = irTemporary(expression)
                    captured.forEach { (value, key, type) ->
                        +builder.irCall(install.symbol).apply {
                            dispatchReceiver = builder.irGetObject(runtime.symbol)
                            typeArguments[0] = type
                            arguments[1] = builder.irGet(node)
                            arguments[2] = builder.irString(key)
                            arguments[3] = builder.irGet(value)
                        }
                    }
                    +builder.irGet(node)
                }.also { it.patchDeclarationParents(owner) }
            }
        })
    }
}

private val inputAnnotation = FqName("io.canopy.engine.core.flows.events.Input")
internal val canopyInputCallOrigin = IrStatementOriginImpl("CANOPY_INPUT_CALL")
