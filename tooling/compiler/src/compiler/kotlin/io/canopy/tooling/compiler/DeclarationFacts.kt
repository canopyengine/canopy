package io.canopy.tooling.compiler

import io.canopy.tooling.compiler.rules.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.FqName

/** Converts each declaration once per validation boundary; facts are discarded before the next rewrite. */
internal class DeclarationFacts(
    private val context: CanopyRuleContext,
    private val messages: DiagnosticMessages,
    private val phase: ValidationPhase,
) {
    fun read(declaration: IrDeclaration): DeclarationInfo {
        val name = (declaration as? IrDeclarationWithName)?.name?.asString()
        val span = messages.span(declaration.file, declaration.startOffset)
        return when (declaration) {
            is IrClass -> {
                val node = context.isNodeSubclass(declaration)
                ClassInfo(
                    name!!,
                    span,
                    node,
                    declaration.isInner,
                    if (node && !declaration.isInner && phase == ValidationPhase.StoredState) {
                        stateFacts(declaration, messages)
                    } else {
                        emptyList()
                    }
                )
            }
            is IrProperty -> {
                val owner = declaration.parent as? IrClass
                val node = owner != null && context.isNodeSubclass(owner)
                val field = declaration.backingField
                val initializer = field?.initializer?.expression
                val assignment = initializer?.let {
                    messages.assignment(declaration.file, declaration.startOffset, it.startOffset)
                }
                val unsupported = if (node &&
                    !owner.isInner &&
                    field != null &&
                    !field.isStatic &&
                    !declaration.isDelegated
                ) {
                    when {
                        declaration.isLateinit -> UnsupportedStorage.Lateinit
                        field.annotations.isNotEmpty() || declaration.hasAnnotation(FqName("kotlin.jvm.JvmField")) ->
                            UnsupportedStorage.FieldAnnotation
                        field.type.classOrNull?.owner?.isValue == true -> UnsupportedStorage.ValueClass
                        else -> null
                    }
                } else {
                    null
                }
                PropertyInfo(
                    name!!, span, node, declaration.isDelegated,
                    initializer?.type?.classFqName?.asString(), initializer is IrCall,
                    assignment?.let { messages.span(declaration.file, it) },
                    assignment?.let { messages.explicitType(declaration.file, declaration.startOffset, it) } ?: true,
                    unsupported
                )
            }
            else -> DeclarationInfo(name, span)
        }
    }
}
