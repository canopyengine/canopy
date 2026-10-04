package io.canopy.tooling.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isSubclassOf
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/**
 * A compile-time validation rule. Providers need a public no-argument constructor and a
 * META-INF/services/io.canopy.tooling.compiler.CanopyCompilerRule entry in their compiler plugin jar.
 * Rules run once for every source declaration, including nested classes and functions, and must not rewrite the IR.
 * This extension API follows the repository's pinned Kotlin compiler version.
 */
interface CanopyCompilerRule {
    /** Unique diagnostic identifier used in compiler output. */
    val id: String

    /** Checks a declaration and reports violations through [context]. */
    fun check(declaration: IrDeclaration, context: CanopyRuleContext)
}

/** Compilation-local compiler services and source diagnostics shared by rules. */
class CanopyRuleContext internal constructor(
    module: IrModuleFragment,
    /** Kotlin services for symbol resolution; tied to the pinned compiler version. */
    val pluginContext: IrPluginContext,
    private val messages: MessageCollector,
) {
    private val nodeBase by lazy {
        pluginContext.referenceClass(ClassId.topLevel(FqName("io.canopy.engine.core.nodes.Node")))?.owner
            ?: module.files.flatMap { it.declarations }.filterIsInstance<IrClass>()
                .firstOrNull { it.fqNameWhenAvailable?.asString() == "io.canopy.engine.core.nodes.Node" }
    }

    /** Includes indirect subclasses, while excluding the audited engine facade itself. */
    fun isNodeSubclass(declaration: IrClass): Boolean {
        val base = nodeBase ?: return false
        return declaration !== base && declaration.isSubclassOf(base)
    }

    /** Emits an error at the offending declaration without retaining compilation objects afterwards. */
    fun report(id: String, declaration: IrDeclaration, explanation: String) {
        val file = declaration.file
        val offset = declaration.startOffset.coerceAtLeast(0)
        val location = CompilerMessageLocation.create(
            file.fileEntry.name,
            file.fileEntry.getLineNumber(offset) + 1,
            file.fileEntry.getColumnNumber(offset) + 1,
            null
        )
        messages.report(CompilerMessageSeverity.ERROR, "$id: $explanation", location)
    }
}
