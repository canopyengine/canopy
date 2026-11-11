package io.canopy.tooling.compiler

import kotlin.test.*
import io.canopy.tooling.compiler.rules.*
import org.junit.jupiter.api.Test

class RuleRegistryTests {
    private fun rule(id: String = "test.rule", diagnostic: DiagnosticSpec = DiagnosticSpec("TEST_RULE")) =
        object : DeclarationRule {
            override val id = id
            override val phase = ValidationPhase.Source
            override val diagnostics = setOf(diagnostic)
            override fun check(declaration: DeclarationInfo, context: RuleContext) = Unit
        }

    private fun provider(id: String = "test.provider", version: Int = RULE_API_VERSION, vararg rules: ValidationRule) =
        object : RuleProvider {
            override val id = id
            override val apiVersion = version
            override fun rules() = rules.toList()
        }

    @Test
    fun `rejects duplicate identities incompatible providers and invalid targets before traversal`() {
        val valid = provider(rules = arrayOf(rule()))
        assertFailsWith<IllegalArgumentException> { RuleRegistry(listOf(valid, valid)) }
        assertFailsWith<IllegalArgumentException> { RuleRegistry(listOf(provider(version = 0))) }
        assertFailsWith<IllegalArgumentException> { RuleRegistry(listOf(provider(rules = arrayOf(rule(), rule())))) }
        val invalid = object : ValidationRule {
            override val id = "test.invalid"
            override val phase = ValidationPhase.Source
            override val diagnostics = setOf(DiagnosticSpec("TEST_INVALID"))
        }
        assertFailsWith<IllegalArgumentException> { RuleRegistry(listOf(provider(rules = arrayOf(invalid)))) }
    }

    @Test
    fun `diagnostic ownership prevents downgrading builtins and conflicting providers`() {
        assertFailsWith<IllegalArgumentException> {
            RuleRegistry(
                listOf(
                    BuiltinRules(),
                    provider(
                        rules = arrayOf(
                            rule(
                                diagnostic = DiagnosticSpec("CANOPY_NODE_PROPERTY_ABI", DiagnosticSeverity.Warning)
                            )
                        )
                    )
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RuleRegistry(
                listOf(
                    provider("one", rules = arrayOf(rule("one"))),
                    provider("two", rules = arrayOf(rule("two")))
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RuleRegistry(
                listOf(
                    provider(
                        rules = arrayOf(
                            rule("one"),
                            rule("two", DiagnosticSpec("TEST_RULE", DiagnosticSeverity.Warning))
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `independent provider discovery order yields identical execution order and metadata snapshots`() {
        val first = provider("a", rules = arrayOf(rule("a", DiagnosticSpec("FIRST"))))
        val second = provider("b", rules = arrayOf(rule("b", DiagnosticSpec("SECOND"))))
        val forward = RuleRegistry(listOf(BuiltinRules(), first, second))
        val reverse = RuleRegistry(listOf(second, first, BuiltinRules()))
        assertEquals(forward.rules.map { it.id }, reverse.rules.map { it.id })
        val mutable = mutableSetOf(DiagnosticSpec("ORIGINAL"))
        val changing = object : DeclarationRule {
            override val id = "changing"
            override val phase = ValidationPhase.Source
            override val diagnostics = mutable
            override fun check(declaration: DeclarationInfo, context: RuleContext) = Unit
        }
        val registry = RuleRegistry(listOf(provider(rules = arrayOf(changing))))
        mutable.clear()
        assertEquals(setOf(DiagnosticSpec("ORIGINAL")), registry.rules.single().diagnostics)
    }
}
