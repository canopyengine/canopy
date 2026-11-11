package io.canopy.tooling.compiler

import io.canopy.tooling.compiler.rules.*

/** Validates source declarations before storage rewriting hides their initializer. */
internal class ReactiveDeclarationRule : PropertyRule {
    override val id = "canopy.reactivity.delegation"
    override val phase = ValidationPhase.Source
    private val delegation = DiagnosticSpec("CANOPY_PROPERTY_DELEGATION")
    override val diagnostics = setOf(delegation)

    override fun check(property: PropertyInfo, context: RuleContext) {
        if (property.isDelegated) return
        val type = property.initializerType
        val descriptor = type in setOf(
            "io.canopy.engine.core.queries.NodeDependency",
            "io.canopy.engine.core.queries.GlobalDependency",
            "io.canopy.engine.data.assets.AssetDelegate"
        )
        val reactive = property.belongsToNode &&
            property.initializerIsCall &&
            type in setOf("io.canopy.engine.core.flows.events.Signal", "io.canopy.engine.core.flows.events.Computed")
        if (!descriptor && !reactive) return
        val kind = when (type) {
            "io.canopy.engine.core.flows.events.Signal" -> "signal"
            "io.canopy.engine.core.flows.events.Computed" -> "computed"
            else -> "reference or asset"
        }
        context.report(
            delegation,
            property.assignmentSpan ?: property.span,
            "Use `by` for a $kind property",
            label = "`=` stores the ${type?.substringAfterLast('.')} object.",
            help = if (property.hasExplicitType) {
                "Use `by` and remove the explicit wrapper type so Kotlin can infer the value type."
            } else {
                "Replace `=` with `by`, then read the property directly."
            },
            replacement = if (property.assignmentSpan == null || property.hasExplicitType) null else "by"
        )
    }
}
