package io.canopy.engine.compiler

import org.jetbrains.kotlin.ir.declarations.IrDeclaration

/** Deliberately collides with a mandatory rule to verify fail-closed provider registration. */
class TestDuplicateRule : CanopyCompilerRule {
    override val id: String = "CANOPY_UNMANAGED_NODE_STATE"

    override fun check(declaration: IrDeclaration, context: CanopyRuleContext) = Unit
}
