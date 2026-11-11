package io.canopy.tooling.compiler

import io.canopy.tooling.compiler.rules.*

/** Validates retained fields and captures after automatic properties have become guarded slots. */
class NodeStateRule : ClassRule {
    override val id = "canopy.state.retention"
    override val phase = ValidationPhase.StoredState
    private val unmanaged = DiagnosticSpec("CANOPY_UNMANAGED_NODE_STATE")
    override val diagnostics = setOf(unmanaged)

    override fun check(declaration: ClassInfo, context: RuleContext) {
        if (!declaration.isNodeSubclass) return
        if (declaration.isInner) {
            context.report(
                unmanaged,
                declaration.span,
                "Node classes cannot be inner",
                label = "An inner class keeps its enclosing instance outside guarded Node storage.",
                help = "Use a nested class and pass required values as constructor properties or by nodeProperty(...)."
            )
            return
        }
        declaration.stateIssues.forEach { issue ->
            when (issue.kind) {
                StateIssueKind.Delegate -> context.report(
                    unmanaged,
                    issue.span,
                    "Use a supported delegate for this Node property",
                    label = "This delegate keeps state outside the Node's guarded storage.",
                    help = "Use by nodeProperty(initial), or by signal(initial) for reactive state."
                )
                StateIssueKind.Field -> context.report(
                    unmanaged,
                    issue.span,
                    "This Node contains an unmanaged instance field",
                    label = "`${issue.name}` would remain on the Node object after destruction.",
                    help = "Store the value with by nodeProperty(initial)."
                )
                StateIssueKind.Capture -> context.report(
                    unmanaged,
                    issue.span,
                    "Pass this value into the Node explicitly",
                    label = "`${issue.name}` is captured outside guarded Node storage.",
                    help = "Declare a constructor property, or store the value with by nodeProperty(...)."
                )
            }
        }
    }
}
