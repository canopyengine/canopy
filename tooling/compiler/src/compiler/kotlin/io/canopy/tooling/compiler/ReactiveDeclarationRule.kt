package io.canopy.tooling.compiler

import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.file

/** Checks user declarations before automatic storage hides their original initializer. */
internal class ReactiveDeclarationRule : CanopyCompilerRule {
    override val id = "CANOPY_PROPERTY_DELEGATION"

    override fun check(declaration: IrDeclaration, context: CanopyRuleContext) {
        if (declaration !is IrProperty || declaration.isDelegated) return
        val field = declaration.backingField ?: return
        val initializer = field.initializer?.expression ?: return
        val type = initializer.type.classFqName?.asString()
        val descriptor = type in setOf(
            "io.canopy.engine.core.queries.NodeDependency",
            "io.canopy.engine.core.queries.GlobalDependency",
            "io.canopy.engine.data.assets.AssetDelegate"
        )
        val owner = declaration.parent as? IrClass
        val reactive = owner != null &&
            context.isNodeSubclass(owner) &&
            initializer is IrCall &&
            type in setOf("io.canopy.engine.core.flows.events.Signal", "io.canopy.engine.core.flows.events.Computed")
        if (!descriptor && !reactive) return
        val file = declaration.file
        val assignment = context.diagnostics.assignment(file, declaration.startOffset, initializer.startOffset)
        val typed = assignment?.let { context.diagnostics.explicitType(file, declaration.startOffset, it) } ?: true
        val kind = when (type) {
            "io.canopy.engine.core.flows.events.Signal" -> "signal"
            "io.canopy.engine.core.flows.events.Computed" -> "computed"
            else -> "reference or asset"
        }
        context.diagnostics.emit(
            id,
            "Use `by` for a $kind property",
            file,
            declaration,
            label = "`=` stores the ${type?.substringAfterLast('.')} object.",
            help = if (typed) {
                "Use `by` and remove the explicit wrapper type so Kotlin can infer the value type."
            } else {
                "Replace `=` with `by`, then read the property directly."
            },
            start = assignment ?: declaration.startOffset,
            replacement = if (assignment == null || typed) null else "by"
        )
    }
}
