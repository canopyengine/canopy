package io.canopy.tooling.compiler.rules

/** API revision checked before any provider is allowed to inspect a compilation. */
const val RULE_API_VERSION: Int = 1

/** Validation boundaries around the compiler's trusted storage transformation. */
enum class ValidationPhase { Source, StoredState }

/** Severity belongs to a diagnostic, independently of a rule's identity. */
enum class DiagnosticSeverity { Error, Warning }

/** A provider owns its diagnostic codes; another provider cannot redefine them. */
data class DiagnosticSpec(val code: String, val severity: DiagnosticSeverity = DiagnosticSeverity.Error)

/** Immutable source position. Compiler-owned spans remain valid only during the current compilation. */
class SourceSpan internal constructor(val path: String, val start: Int, val length: Int)

/** Common source declaration facts. No Kotlin IR or compiler services escape into the rule API. */
open class DeclarationInfo internal constructor(val name: String?, val span: SourceSpan)

/** Physical storage forms that cannot become guarded Node slots. */
enum class UnsupportedStorage { Lateinit, FieldAnnotation, ValueClass }

/** Facts about a property before or after storage rewriting, as selected by the rule's phase. */
class PropertyInfo internal constructor(
    name: String,
    span: SourceSpan,
    val belongsToNode: Boolean,
    val isDelegated: Boolean,
    val initializerType: String?,
    val initializerIsCall: Boolean,
    val assignmentSpan: SourceSpan?,
    val hasExplicitType: Boolean,
    val unsupportedStorage: UnsupportedStorage?,
) : DeclarationInfo(name, span)

/** Why a Node would retain unmanaged state after storage rewriting. */
enum class StateIssueKind { Delegate, Field, Capture }

/** An immutable observation; the rule decides whether and how to diagnose it. */
class StateIssue internal constructor(val kind: StateIssueKind, val name: String, val span: SourceSpan)

/** Class facts include storage/capture observations only in the StoredState phase. */
class ClassInfo internal constructor(
    name: String,
    span: SourceSpan,
    val isNodeSubclass: Boolean,
    val isInner: Boolean,
    val stateIssues: List<StateIssue>,
) : DeclarationInfo(name, span)

/** One validator has exactly one target interface and declares every diagnostic it may emit. */
interface ValidationRule {
    val id: String
    val phase: ValidationPhase
    val diagnostics: Set<DiagnosticSpec>
}

/** Checks every property, including properties outside Nodes. */
interface PropertyRule : ValidationRule {
    fun check(property: PropertyInfo, context: RuleContext)
}

/** Checks each class once. */
interface ClassRule : ValidationRule {
    fun check(declaration: ClassInfo, context: RuleContext)
}

/** Checks all declarations, including nested functions and generated accessors. */
interface DeclarationRule : ValidationRule {
    fun check(declaration: DeclarationInfo, context: RuleContext)
}

/** Register a public no-argument implementation using META-INF/services and a compiler-only dependency. */
interface RuleProvider {
    val id: String
    val apiVersion: Int get() = RULE_API_VERSION
    fun rules(): List<ValidationRule>
}

/** A rule-scoped reporting capability. Undeclared diagnostics and foreign source spans are rejected. */
interface RuleContext {
    fun report(
        diagnostic: DiagnosticSpec,
        at: SourceSpan,
        title: String,
        label: String? = null,
        help: String? = null,
        replacement: String? = null,
    )
}
