package io.canopy.engine.compiler

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction

/** Independent rule provider used to verify compiler plugin service discovery. */
class TestNamingRule : CanopyCompilerRule {
    override val id: String = "CANOPY_TEST_NAMING"

    override fun check(declaration: IrDeclaration, context: CanopyRuleContext) {
        if (declaration !is IrClass && declaration !is IrSimpleFunction && declaration !is IrProperty) return
        if (declaration is IrDeclarationWithName && declaration.name.asString().startsWith("Forbidden")) {
            context.report(id, declaration, "This declaration name is reserved by the test rule")
        }
    }
}
