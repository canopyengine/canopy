package io.canopy.tooling.compiler

import io.canopy.tooling.compiler.rules.*
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/** One compilation owns its registry, diagnostics and phase-local facts. Rewrites have an explicit fixed order. */
internal class CompilerPipeline(private val messages: DiagnosticMessages, private val registry: RuleRegistry) :
    IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        validate(ValidationPhase.Source, moduleFragment, pluginContext)
        if (messages.errorCount > 0) return
        NodePropertyTransform(messages).generate(moduleFragment, pluginContext)
        if (messages.errorCount > 0) return
        validate(ValidationPhase.StoredState, moduleFragment, pluginContext)
        if (messages.errorCount > 0) return
        for (stage in listOf(
            NodeOwnershipTransform(messages),
            UiExpressionTransform(messages),
            NodeConstructionTransform(messages)
        )) {
            stage.generate(moduleFragment, pluginContext)
            if (messages.errorCount > 0) return
        }
    }

    private fun validate(phase: ValidationPhase, module: IrModuleFragment, pluginContext: IrPluginContext) {
        val services = CanopyRuleContext(module, pluginContext, messages)
        val facts = DeclarationFacts(services, messages, phase)
        // Build rule-scoped reporters from validated metadata. Facts are shared, immutable views.
        val rules = registry.rules.filter { it.phase == phase }.map { rule ->
            val allowed = rule.diagnostics.toSet()
            rule to object : RuleContext {
                override fun report(
                    diagnostic: DiagnosticSpec,
                    at: SourceSpan,
                    title: String,
                    label: String?,
                    help: String?,
                    replacement: String?,
                ) {
                    require(diagnostic in allowed) {
                        "Rule ${rule.id} reported undeclared diagnostic ${diagnostic.code}"
                    }
                    messages.report(diagnostic, at, title, label, help, replacement)
                }
            }
        }
        val legacy = if (phase == ValidationPhase.StoredState) {
            registry.legacyRules.map { rule ->
                rule to CanopyRuleContext(module, pluginContext, messages, rule.id)
            }
        } else {
            emptyList()
        }
        val failed = mutableSetOf<String>()
        fun check(id: String, action: () -> Unit) {
            if (id in failed) return
            try {
                action()
            } catch (failure: Exception) {
                failed += id
                messages.emit(
                    "CANOPY_RULE_PROVIDER",
                    "Compiler rule $id failed",
                    label = failure.message ?: failure.javaClass.simpleName,
                    help = "Fix or remove this rule provider; mandatory Canopy checks remain installed."
                )
            }
        }
        module.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }
            override fun visitDeclaration(declaration: IrDeclarationBase) {
                val info = facts.read(declaration)
                rules.forEach { (rule, context) ->
                    check(rule.id) {
                        val implementation = rule.implementation
                        when {
                            implementation is PropertyRule && info is PropertyInfo -> implementation.check(
                                info,
                                context
                            )
                            implementation is ClassRule && info is ClassInfo -> implementation.check(info, context)
                            implementation is DeclarationRule -> implementation.check(info, context)
                        }
                    }
                }
                legacy.forEach { (rule, context) -> check(rule.id) { rule.check(declaration, context) } }
                declaration.acceptChildrenVoid(this)
            }
        })
    }
}
