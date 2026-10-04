package io.canopy.engine.compiler

import org.jetbrains.kotlin.ir.declarations.IrClass

/** Independent rule provider used to verify compiler plugin service discovery. */
class TestNamingRule : CanopyCompilerRule {
    override val id: String = "CANOPY_TEST_NAMING"

    override fun check(declaration: IrClass, context: CanopyRuleContext) {
        if (declaration.name.asString() == "Forbidden") {
            context.report(id, declaration, "This class name is reserved by the test rule")
        }
    }
}
