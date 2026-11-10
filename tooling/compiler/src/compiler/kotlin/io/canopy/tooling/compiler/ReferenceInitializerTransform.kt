package io.canopy.tooling.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Isolated ancestor-initializer experiment. Stable delegated queries are deliberately untouched. */
internal class ReferenceInitializerTransform(private val messages: MessageCollector) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val context = CanopyRuleContext(moduleFragment, pluginContext, messages)
        val references = linkedMapOf<IrFieldSymbol, IrProperty>()
        val consumed = mutableSetOf<IrCall>()
        moduleFragment.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) = element.acceptChildrenVoid(this)
            override fun visitProperty(declaration: IrProperty) {
                val field = declaration.backingField
                val initializer = field?.initializer?.expression
                val marker = initializer as? IrCall
                if (marker?.symbol?.owner?.fqNameWhenAvailable?.asString() != MARKER) {
                    declaration.acceptChildrenVoid(this)
                    return
                }
                consumed += marker
                val owner = declaration.parent as? IrClass
                val reason = when {
                    owner == null ||
                        !context.isNodeSubclass(
                            owner
                        ) -> "requires an instance property on a Node subclass"
                    field.isStatic || declaration.isVar || declaration.isDelegated -> "requires a non-delegated val"
                    declaration.getter?.origin != IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR ->
                        "cannot replace a custom getter"
                    field.type.isNullable() -> "nullable reference properties are outside this first prototype"
                    declaration.hasAnnotation(FqName("kotlin.jvm.JvmField")) || field.annotations.isNotEmpty() ->
                        "cannot provide a physical JVM field"
                    else -> null
                }
                if (reason != null) {
                    context.report(ID, declaration, "${declaration.name}: $reason; use the stable delegated API")
                } else {
                    references[field.symbol] = declaration
                    val getter = declaration.getter!!
                    val resolver = pluginContext.referenceFunctions(
                        CallableId(FqName(PACKAGE), Name.identifier("resolveAncestor"))
                    ).singleOrNull()
                    if (resolver == null) {
                        context.report(ID, declaration, "requires the matching reference-initializer prototype runtime")
                    } else {
                        val builder = DeclarationIrBuilder(pluginContext, getter.symbol)
                        getter.origin = IrDeclarationOrigin.DEFINED
                        getter.body = builder.irBlockBody {
                            +irReturn(
                                irCall(resolver, field.type).apply {
                                    typeArguments[0] = marker.typeArguments[0]
                                    arguments[0] = irGet(getter.dispatchReceiverParameter!!)
                                    arguments[1] = IrClassReferenceImpl(
                                        startOffset = marker.startOffset,
                                        endOffset = marker.endOffset,
                                        type = pluginContext.irBuiltIns.kClassClass.typeWith(marker.typeArguments[0]!!),
                                        symbol = marker.typeArguments[0]!!.classifierOrNull!!,
                                        classType = marker.typeArguments[0]!!
                                    )
                                    arguments[2] = irString(declaration.name.asString())
                                }
                            )
                        }
                        field.initializer = null
                        declaration.backingField = null
                    }
                }
                declaration.acceptChildrenVoid(this)
            }
        })
        // Kotlin may access private default properties directly. Route those accesses through the live getter too.
        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitGetField(expression: IrGetField): IrExpression {
                expression.transformChildrenVoid(this)
                val property = references[expression.symbol] ?: return expression
                val builder = DeclarationIrBuilder(pluginContext, property.getter!!.symbol)
                return builder.irCall(property.getter!!.symbol).apply { dispatchReceiver = expression.receiver }
            }
        })
        moduleFragment.acceptChildrenVoid(object : IrVisitorVoid() {
            private var declaration: IrDeclaration? = null
            override fun visitElement(element: IrElement) = element.acceptChildrenVoid(this)
            override fun visitDeclaration(element: IrDeclarationBase) {
                val previous = declaration
                declaration = element
                element.acceptChildrenVoid(this)
                declaration = previous
            }
            override fun visitTypeOperator(expression: IrTypeOperatorCall) {
                val call = expression.argument as? IrCall
                val property = call?.symbol?.owner?.correspondingPropertySymbol?.owner
                if (expression.operator == IrTypeOperator.IMPLICIT_CAST && property in references.values) {
                    declaration?.let {
                        context.report(
                            "CANOPY_REFERENCE_SMART_CAST",
                            it,
                            "Live reference '${property!!.name}' cannot be smart-cast; " +
                                "capture a local val before narrowing its type"
                        )
                    }
                }
                expression.acceptChildrenVoid(this)
            }
            override fun visitCall(expression: IrCall) {
                if (expression.symbol.owner.fqNameWhenAvailable?.asString() == MARKER && expression !in consumed) {
                    declaration?.let {
                        context.report(
                            ID,
                            it,
                            "ancestor<T>() must be a direct Node val initializer; " +
                                "use the stable delegated API elsewhere"
                        )
                    }
                }
                expression.acceptChildrenVoid(this)
            }
        })
    }

    private companion object {
        const val PACKAGE = "io.canopy.engine.core.queries.experimental"
        const val MARKER = "$PACKAGE.ancestor"
        const val ID = "CANOPY_REFERENCE_INITIALIZER"
    }
}
