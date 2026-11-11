package io.canopy.tooling.compiler

import io.canopy.tooling.compiler.rules.*

/** Validates the complete catalog before inspecting source. Built-in diagnostics reserve their codes. */
internal class RuleRegistry(providers: List<RuleProvider>, legacy: List<CanopyCompilerRule> = emptyList()) {
    val rules: List<RegisteredRule>
    val legacyRules: List<CanopyCompilerRule> = legacy.sortedBy { it.id }

    init {
        require(providers.map { it.id }.distinct().size == providers.size) { "Provider IDs must be unique" }
        val ruleIds = mutableSetOf<String>()
        val diagnosticOwners = mutableMapOf<String, String>()
        reservedDiagnostics.forEach { diagnosticOwners[it] = "canopy.builtin" }
        rules = providers.sortedBy { it.id }.flatMap { provider ->
            require(provider.id.isNotBlank()) { "Provider IDs must not be blank" }
            require(provider.apiVersion == RULE_API_VERSION) { "Unsupported rule API version for ${provider.id}" }
            provider.rules().toList().map { implementation ->
                val rule =
                    RegisteredRule(
                        implementation.id,
                        implementation.phase,
                        implementation.diagnostics.toSet(),
                        implementation
                    )
                require(rule.id.isNotBlank() && ruleIds.add(rule.id)) { "Canopy compiler rule IDs must be unique" }
                val targets = listOf(
                    implementation is PropertyRule,
                    implementation is ClassRule,
                    implementation is DeclarationRule
                ).count {
                    it
                }
                require(targets == 1) { "Rule ${rule.id} must declare exactly one supported target" }
                require(rule.diagnostics.isNotEmpty()) { "Rule ${rule.id} must declare diagnostics" }
                require(rule.diagnostics.map { it.code }.distinct().size == rule.diagnostics.size) {
                    "Rule ${rule.id} declares conflicting diagnostic definitions"
                }
                rule.diagnostics.forEach { spec ->
                    require(spec.code.matches(Regex("[A-Z][A-Z0-9_]*"))) { "Invalid diagnostic code ${spec.code}" }
                    val owner = diagnosticOwners.putIfAbsent(spec.code, provider.id)
                    require(owner == null || owner == provider.id) { "Diagnostic ${spec.code} is owned by $owner" }
                    if (owner == "canopy.builtin") {
                        require(spec.severity == DiagnosticSeverity.Error) {
                            "Mandatory diagnostics cannot be downgraded"
                        }
                    }
                }
                rule
            }
        }.sortedBy { it.id }
        val definitions = mutableMapOf<String, DiagnosticSpec>()
        rules.forEach { rule ->
            rule.diagnostics.forEach { spec ->
                require(definitions.putIfAbsent(spec.code, spec).let { it == null || it == spec }) {
                    "Conflicting diagnostic definition for ${spec.code}"
                }
            }
        }
        legacyRules.forEach { rule ->
            require(rule.id.isNotBlank() && ruleIds.add(rule.id) && rule.id !in diagnosticOwners) {
                "Canopy compiler rule IDs must be unique"
            }
        }
    }

    companion object {
        val reservedDiagnostics = setOf(
            "CANOPY_PROPERTY_DELEGATION", "CANOPY_UNSUPPORTED_NODE_PROPERTY", "CANOPY_UNMANAGED_NODE_STATE",
            "CANOPY_NODE_PROPERTY_ABI", "CANOPY_OWNERSHIP_ABI", "CANOPY_UI_UNSUPPORTED",
            "CANOPY_NODE_CONSTRUCTION_ABI", "CANOPY_NODE_CONSTRUCTION_REFERENCE", "CANOPY_NODE_CONSTRUCTION_SUSPEND",
            "CANOPY_RULE_ID_CONFLICT", "CANOPY_RULE_PROVIDER", "CANOPY_KOTLIN_VERSION", "CANOPY_DIAGNOSTIC_COLOR"
        )
    }
}

/** Mandatory provider is constructed directly and cannot be replaced through service discovery. */
internal class BuiltinRules : RuleProvider {
    override val id = "canopy.builtin"
    override fun rules() = listOf(ReactiveDeclarationRule(), NodeStorageRule(), NodeStateRule())
}

/** Metadata is copied once, so a provider cannot change its privileges between phases. */
internal data class RegisteredRule(
    val id: String,
    val phase: ValidationPhase,
    val diagnostics: Set<DiagnosticSpec>,
    val implementation: ValidationRule,
)
