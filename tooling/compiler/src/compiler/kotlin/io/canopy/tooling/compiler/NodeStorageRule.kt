package io.canopy.tooling.compiler

import io.canopy.tooling.compiler.rules.*

/** Rejects storage forms which cannot be rewritten without changing their Kotlin contract. */
internal class NodeStorageRule : PropertyRule {
    override val id = "canopy.state.storage"
    override val phase = ValidationPhase.Source
    private val unsupported = DiagnosticSpec("CANOPY_UNSUPPORTED_NODE_PROPERTY")
    override val diagnostics = setOf(unsupported)

    override fun check(property: PropertyInfo, context: RuleContext) {
        val reason = property.unsupportedStorage ?: return
        val late = reason == UnsupportedStorage.Lateinit
        context.report(
            unsupported,
            property.span,
            when (reason) {
                UnsupportedStorage.Lateinit -> "`lateinit` cannot be used on Node properties"
                UnsupportedStorage.FieldAnnotation -> "Field annotations cannot be used on Node properties"
                UnsupportedStorage.ValueClass -> "This value-class property needs explicit Node storage"
            },
            label = when (reason) {
                UnsupportedStorage.Lateinit -> "`lateinit` cannot use guarded Node storage."
                UnsupportedStorage.FieldAnnotation ->
                    "This field annotation requires a JVM field, which automatic Node storage replaces."
                UnsupportedStorage.ValueClass -> "Value-class storage is not supported."
            },
            help = if (late) {
                "Give it an initial value, use a nullable property, or use by nodeProperty(...)."
            } else if (reason == UnsupportedStorage.FieldAnnotation) {
                "Remove the field annotation; ordinary Node properties are stored automatically."
            } else {
                "Store the value with by nodeProperty(initial)."
            }
        )
    }
}
