package io.canopy.tooling.compiler

import io.canopy.tooling.compiler.rules.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/** Trusted IR analysis. Returned observations expose no mutable compiler objects. */
internal fun stateFacts(declaration: IrClass, messages: DiagnosticMessages): List<StateIssue> {
    val issues = mutableListOf<StateIssue>()
    declaration.declarations.filterIsInstance<IrProperty>().forEach { property ->
        val field = property.backingField
        if (field != null &&
            !field.isStatic &&
            (
                !property.isDelegated ||
                    field.type.classFqName?.asString() !in setOf(
                        "io.canopy.engine.core.nodes.NodeProperty",
                        "io.canopy.engine.core.flows.events.SignalProperty",
                        "io.canopy.engine.core.flows.events.ComputedProperty",
                        "io.canopy.engine.core.queries.GlobalDependency",
                        "io.canopy.engine.core.queries.NodeDependency",
                        "io.canopy.engine.data.assets.AssetDelegate"
                    )
                )
        ) {
            issues += StateIssue(
                StateIssueKind.Delegate,
                property.name.asString(),
                messages.span(declaration.file, (field.initializer?.expression ?: property).startOffset)
            )
        }
    }
    declaration.declarations.filterIsInstance<IrField>().forEach { field ->
        if (!field.isStatic && field.correspondingPropertySymbol == null) {
            issues += StateIssue(
                StateIssueKind.Field,
                field.name.asString(),
                messages.span(declaration.file, field.startOffset)
            )
        }
    }
    // Capture fields appear during later JVM lowering; inspect outer value reads before they are synthesized.
    val bodies = declaration.declarations.flatMap { member ->
        when (member) {
            is IrFunction -> listOfNotNull(member.body)
            is IrAnonymousInitializer -> listOf(member.body)
            is IrProperty -> listOfNotNull(
                member.backingField?.initializer,
                member.getter?.body,
                member.setter?.body
            )
            else -> emptyList()
        }.map { body ->
            Triple(
                member,
                body,
                member is IrConstructor ||
                    member is IrAnonymousInitializer ||
                    (member is IrProperty && body === member.backingField?.initializer)
            )
        }
    }
    val captured = mutableSetOf<org.jetbrains.kotlin.ir.symbols.IrValueSymbol>()
    bodies.forEach { (member, body, initializing) ->
        body.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }
            override fun visitGetValue(expression: IrGetValue) {
                val value = expression.symbol.owner
                var container: IrDeclarationParent? = value.parent
                var belongsToNode = value === declaration.thisReceiver
                while (container is IrDeclaration) {
                    if (container === declaration) {
                        belongsToNode = true
                        break
                    }
                    container = container.parent
                }
                val constructorCapture = !initializing &&
                    value.parent is IrConstructor &&
                    (value as? IrValueParameter)?.kind == IrParameterKind.Regular
                if (!belongsToNode || constructorCapture) {
                    if (captured.add(expression.symbol)) {
                        issues += StateIssue(
                            StateIssueKind.Capture,
                            value.name.asString(),
                            messages.span(declaration.file, expression.startOffset, value.name.asString().length)
                        )
                    }
                }
            }
        })
    }
    return java.util.Collections.unmodifiableList(issues)
}
