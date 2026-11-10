package io.canopy.tooling.compiler

import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.classFqName

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
        val keyword = if (type == "io.canopy.engine.core.flows.events.Signal") "var" else "val"
        val example = when (type) {
            "io.canopy.engine.core.flows.events.Signal" -> "signal(initial)"
            "io.canopy.engine.core.flows.events.Computed" -> "computed { expression }"
            else -> "the reference or asset factory"
        }
        context.report(
            id,
            declaration,
            "Use '$keyword ${declaration.name} by $example' and read the property directly. " +
                "For an explicit observable handle, construct Signal or Computed with an explicit owner; " +
                "ordinary locals and observable parameters remain supported."
        )
    }
}
