package io.canopy.tooling.compiler

import io.canopy.tooling.compiler.rules.*

/** Independent validator proves a failing rule cannot stop other checks in the same phase. */
class TestSecondProvider :
    RuleProvider,
    ClassRule {
    override val id = "test.second"
    override val phase = ValidationPhase.Source
    private val error = DiagnosticSpec("TEST_SECOND_CHECK")
    override val diagnostics = setOf(error)
    override fun rules(): List<ValidationRule> = listOf(this)

    override fun check(declaration: ClassInfo, context: RuleContext) {
        if (declaration.name == "Crash" || declaration.name == "Forbidden") {
            context.report(error, declaration.span, "Independent check completed")
        }
    }
}
