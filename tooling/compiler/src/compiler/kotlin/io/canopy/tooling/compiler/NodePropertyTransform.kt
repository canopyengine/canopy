package io.canopy.tooling.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrAnonymousInitializerSymbolImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/** Internal storage lowering. External rule providers continue to receive validation-only IR services. */
internal class NodePropertyTransform(private val messages: MessageCollector) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val context = CanopyRuleContext(moduleFragment, pluginContext, messages)
        val fields = linkedMapOf<IrFieldSymbol, Slot>()
        val classes = mutableListOf<IrClass>()
        moduleFragment.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }
            override fun visitClass(declaration: IrClass) {
                if (context.isNodeSubclass(declaration) && !declaration.isInner) classes += declaration
                declaration.acceptChildrenVoid(this)
            }
        })
        classes.forEach { owner ->
            owner.declarations.filterIsInstance<IrProperty>().forEach propertyLoop@{ property ->
                val field = property.backingField ?: return@propertyLoop
                if (field.isStatic || property.isDelegated) return@propertyLoop
                val unsupported = when {
                    property.isLateinit -> "lateinit is not supported; use nullable automatic state or nodeProperty"
                    field.annotations.isNotEmpty() ||
                        property.hasAnnotation(
                            FqName("kotlin.jvm.JvmField")
                        ) -> "field annotations and @JvmField require a physical field; use nodeProperty"
                    field.type.classOrNull?.owner?.isValue == true ->
                        "value-class storage is not supported; use nodeProperty"
                    else -> null
                }
                if (unsupported != null) {
                    context.report(
                        "CANOPY_UNSUPPORTED_NODE_PROPERTY",
                        property,
                        "${owner.name}.${property.name}: $unsupported"
                    )
                    return@propertyLoop
                }
                val ownerKey = owner.fqNameWhenAvailable?.asString()
                    ?: "${owner.file.fileEntry.name}:${owner.startOffset}:${owner.name}"
                fields[field.symbol] =
                    Slot(
                        owner,
                        property,
                        "${ownerKey.length}:$ownerKey${property.name.asString().length}:${property.name}"
                    )
            }
        }
        if (fields.isEmpty()) return
        val node = pluginContext.referenceClass(ClassId.topLevel(FqName("io.canopy.engine.core.nodes.Node")))!!.owner
        fun compatible(function: IrSimpleFunction, name: String, getter: Boolean): Boolean {
            if (function.name.asString() != name ||
                function.typeParameters.size != 1 ||
                function.parameters.size != 3
            ) {
                return false
            }
            if (function.visibility != DescriptorVisibilities.PROTECTED ||
                function.modality != Modality.FINAL ||
                function.parameters[0].kind != IrParameterKind.DispatchReceiver ||
                function.parameters.drop(1).any {
                    it.kind != IrParameterKind.Regular || it.varargElementType != null
                } ||
                function.parameters[1].type != pluginContext.irBuiltIns.stringType ||
                function.parameters[2].type != function.typeParameters.single().defaultType ||
                function.isSuspend
            ) {
                return false
            }
            return if (getter) {
                function.returnType == function.typeParameters.single().defaultType
            } else {
                function.returnType == pluginContext.irBuiltIns.unitType
            }
        }
        val get = node.functions.singleOrNull { compatible(it, "compilerPropertyGet", true) }
        val set = node.functions.singleOrNull { compatible(it, "compilerPropertySet", false) }
        if (get == null || set == null) {
            fields.values.forEach { slot ->
                context.report(
                    "CANOPY_NODE_PROPERTY_ABI",
                    slot.property,
                    "Automatic storage requires the matching Canopy runtime with " +
                        "compilerPropertyGet/compilerPropertySet; " +
                        "update the runtime and compiler together or use explicit nodeProperty"
                )
            }
            return
        }
        // Initializers keep their declaration positions; constructor parameter values remain available.
        classes.forEach { owner ->
            val rewritten = mutableListOf<IrDeclaration>()
            owner.declarations.forEach { declaration ->
                rewritten += declaration
                if (declaration is IrProperty) {
                    val field = declaration.backingField
                    val slot = field?.let { fields[it.symbol] }
                    if (slot != null) {
                        declaration.backingField = null
                        // Default-accessor lowering otherwise substitutes accesses with the removed backing field.
                        declaration.getter?.origin = IrDeclarationOrigin.DEFINED
                        declaration.setter?.origin = IrDeclarationOrigin.DEFINED
                        field.initializer?.let { initializer ->
                            val block = pluginContext.irFactory.createAnonymousInitializer(
                                field.startOffset,
                                field.endOffset,
                                IrDeclarationOrigin.DEFINED,
                                IrAnonymousInitializerSymbolImpl(),
                                false
                            ).apply { parent = owner }
                            val builder =
                                DeclarationIrBuilder(pluginContext, block.symbol, field.startOffset, field.endOffset)
                            block.body = builder.irBlockBody {
                                +builder.irCall(
                                    slot.owner.functions.single {
                                        compatible(it, "compilerPropertySet", false)
                                    }.symbol
                                ).apply {
                                    typeArguments[0] = field.type
                                    dispatchReceiver = builder.irGet(owner.thisReceiver!!)
                                    arguments[1] = builder.irString(slot.key)
                                    arguments[2] = initializer.expression
                                }
                            }
                            rewritten += block
                        }
                    }
                }
            }
            owner.declarations.clear()
            owner.declarations.addAll(rewritten)
        }
        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitGetField(expression: IrGetField): IrExpression {
                expression.transformChildrenVoid(this)
                val slot = fields[expression.symbol] ?: return expression
                val builder =
                    DeclarationIrBuilder(pluginContext, slot.owner.symbol, expression.startOffset, expression.endOffset)
                return builder.irCall(
                    slot.owner.functions.single {
                        compatible(it, "compilerPropertyGet", true)
                    }.symbol,
                    expression.type
                ).apply {
                    typeArguments[0] = expression.type
                    dispatchReceiver = expression.receiver
                    arguments[1] = builder.irString(slot.key)
                    arguments[2] = defaultValue(expression)
                }
            }
            override fun visitSetField(expression: IrSetField): IrExpression {
                expression.transformChildrenVoid(this)
                val slot = fields[expression.symbol] ?: return expression
                val builder =
                    DeclarationIrBuilder(pluginContext, slot.owner.symbol, expression.startOffset, expression.endOffset)
                return builder.irCall(
                    slot.owner.functions.single {
                        compatible(it, "compilerPropertySet", false)
                    }.symbol
                ).apply {
                    typeArguments[0] = expression.symbol.owner.type
                    dispatchReceiver = expression.receiver
                    arguments[1] = builder.irString(slot.key)
                    arguments[2] = expression.value
                }
            }
        })
    }

    private fun defaultValue(expression: IrGetField): IrExpression {
        val start = expression.startOffset
        val end = expression.endOffset
        val type = expression.type
        if (type.isNullable()) return IrConstImpl.constNull(start, end, type)
        return when (type.classFqName?.asString()) {
            "kotlin.Boolean" -> IrConstImpl.boolean(start, end, type, false)
            "kotlin.Byte" -> IrConstImpl.byte(start, end, type, 0)
            "kotlin.Short" -> IrConstImpl.short(start, end, type, 0)
            "kotlin.Int" -> IrConstImpl.int(start, end, type, 0)
            "kotlin.Long" -> IrConstImpl.long(start, end, type, 0L)
            "kotlin.Float" -> IrConstImpl.float(start, end, type, 0F)
            "kotlin.Double" -> IrConstImpl.double(start, end, type, 0.0)
            "kotlin.Char" -> IrConstImpl.char(start, end, type, '\u0000')
            else -> IrConstImpl.constNull(start, end, type)
        }
    }

    private data class Slot(val owner: IrClass, val property: IrProperty, val key: String)
}
