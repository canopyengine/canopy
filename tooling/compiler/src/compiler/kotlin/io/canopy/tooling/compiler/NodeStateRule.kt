package io.canopy.tooling.compiler

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/**
 * Mandatory storage and capture checks for direct and indirect custom Node subclasses.
 * Automatic properties have already become engine slots; only approved delegates retain instance storage.
 * Companion state is exempt.
 * This rule validates declarations without rewriting code, while runtime validation protects precompiled classes.
 */
class NodeStateRule : CanopyCompilerRule {
    override val id: String = "CANOPY_UNMANAGED_NODE_STATE"

    override fun check(declaration: IrDeclaration, context: CanopyRuleContext) {
        if (declaration !is IrClass) return
        if (!context.isNodeSubclass(declaration)) return
        if (declaration.isInner) {
            context.report(
                id,
                declaration,
                "${declaration.name}: inner node classes retain their enclosing instance; " +
                    "use a nested class and 'by nodeProperty(...)' for explicit state"
            )
        }
        declaration.declarations.filterIsInstance<IrProperty>().forEach { property ->
            val field = property.backingField
            if (field != null &&
                !field.isStatic &&
                (
                    !property.isDelegated ||
                        field.type.classFqName?.asString() !in setOf(
                            "io.canopy.engine.core.nodes.NodeProperty",
                            "io.canopy.engine.core.queries.GlobalDependency",
                            "io.canopy.engine.core.queries.NodeDependency",
                            "io.canopy.engine.data.assets.AssetDelegate"
                        )
                    )
            ) {
                val keyword = if (property.isVar) "var" else "val"
                context.report(
                    id,
                    property,
                    "${declaration.name}.${property.name}: use " +
                        "'$keyword ${property.name} by nodeProperty(initial)'"
                )
            }
        }
        declaration.declarations.filterIsInstance<IrField>().forEach { field ->
            if (!field.isStatic && field.correspondingPropertySymbol == null) {
                context.report(
                    id,
                    field,
                    "${declaration.name}.${field.name}: unmanaged instance field; " +
                        "implement delegation explicitly with 'var state by nodeProperty(initial)'"
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
                        context.report(
                            id,
                            member,
                            "${declaration.name} captures '${value.name}'; " +
                                "pass it explicitly and store it using 'by nodeProperty(...)'"
                        )
                    }
                }
            })
        }
    }
}
