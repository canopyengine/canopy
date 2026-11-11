package io.canopy.tooling.compiler

import io.canopy.tooling.compiler.rules.*

/** Packed alone into an independent provider JAR; no compiler implementation classes travel with it. */
class TestFactsProvider :
    RuleProvider,
    ClassRule {
    override val id = "test.facts"
    override val phase = ValidationPhase.Source
    private val warning = DiagnosticSpec("TEST_NAME_WARNING", DiagnosticSeverity.Warning)
    private val error = DiagnosticSpec("TEST_NAME_ERROR")
    override val diagnostics = setOf(warning, error)
    override fun rules(): List<ValidationRule> = listOf(this)

    override fun check(declaration: ClassInfo, context: RuleContext) {
        when (declaration.name) {
            "Warn" -> context.report(warning, declaration.span, "Choose a clearer name", help = "Rename Warn.")
            "Forbidden" -> context.report(error, declaration.span, "This name is reserved")
            "Undeclared" -> context.report(DiagnosticSpec("NOT_DECLARED"), declaration.span, "Invalid report")
            "Crash" -> error("Broken provider")
        }
    }
}
