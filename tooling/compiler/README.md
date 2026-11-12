# Canopy compiler tooling

`tooling/compiler` is one included-build module containing compiler transformations, checks and
Gradle integration. Compiler and Gradle sources share the `io.canopy.tooling.compiler` package root;
Gradle-specific classes live in its `gradle` subpackage.

The module builds two isolated artifacts at the engine version:

| Source set | Artifact | Execution host |
| --- | --- | --- |
| `compiler` | `io.github.canopyengine:canopy-compiler` | Kotlin compiler |
| `main` | `io.github.canopyengine:canopy-compiler-gradle` | Gradle |

The compiler artifact contains its registrar, rule SPI, checks and compiler service
registration. The Gradle artifact contains only integration classes and the plugin
descriptor. Compiler APIs and Gradle APIs are provided by their respective hosts;
we do not bundle either host into the other artifact. The composite build selects
the compiler-specific capability when resolving the compiler dependency.

## Compilation pipeline

One compilation-local pipeline runs these ordered stages before JVM bytecode generation:

1. **Source validation:** diagnose accidental non-delegated observable/reference properties and unsupported storage.
2. **Node storage:** supported instance properties become guarded NodeState slots.
3. **Stored-state validation:** mandatory NodeState checks and providers inspect immutable declaration facts.
4. **Ownership:** infer the listening/creating Node for implicit Canopy operations.
5. **UI expression capture:** supported declarative expressions become reevaluable
   bindings and structural regions.
6. **Construction protection:** supported Node constructor calls receive synchronous
   rollback boundaries.

The plugin generates calls to runtime helpers; it does not implement the engine loop
or convert every value into a signal. Keep engine, compiler plugin and Kotlin versions
matched, and recompile consuming modules after changes to these generated contracts.

## Declarative UI capture

Within the supported `UiScope` DSL, direct expressions such as
`Text("Population: ${population()}")` can track signal reads and reevaluate text.
Supported conditionals and keyed lists reconcile structure while preserving retained
identities. Container initialization and action callbacks are not rerun as arbitrary
side effects whenever a property changes. The runtime owns invalidation, layout,
focus and cleanup; platform backends measure and paint their specific components.

Capture applies to the resolved Canopy declarative API, not arbitrary Kotlin calls.
A helper returning an already computed string does not establish a general automatic
reactivity guarantee. Reusable UI declaration functions must return `Unit`; unsupported
structural forms produce source diagnostics. Without the plugin, use the runtime's
explicit binding/structural APIs where appropriate. Refer to the
[shared UI guide](https://github.com/canopyengine/canopy-docs/blob/main/markdown/manuals/concepts/app/declarative-ui.md)
for supported syntax and extension contracts.

## Diagnostic presentation

Canopy reports one structured compiler error per problem, retaining Kotlin's canonical
source location for IDE navigation. The message contains a short title, a bounded source
excerpt, a caret label and concrete help. Safe suggestions show `-`/`+` source lines.
Compatibility failures produce one build-level error rather than repeated field errors.
Existing diagnostic IDs and the legacy rule-provider `report` API remain available.

Colors use standard ANSI terminal colors; they never determine meaning on their own.
For Gradle, `--console=rich` enables automatic diagnostic colors. `--console=plain`,
`NO_COLOR` and recognized IDE builds keep Canopy messages uncolored. CI stays plain
in automatic mode. `-Pcanopy.diagnostics.color=auto|always|never` controls the preference;
plain-console, IDE and NO_COLOR safeguards take priority. Automatic mode is conservative
when Gradle has not confirmed rich-console output. Kotlin and Gradle retain their own
prefixes, task summaries and native diagnostics.

The formatter has no compiler/Gradle dependencies and is compiled independently into
each host artifact from `src/shared/kotlin`. No renderer prints a second copy beside
the compiler message. Source text is cached only for the current compilation and mapped
to Kotlin's normalized offsets, including CRLF files. Missing or synthetic source text
falls back to the structured location and help. Tabs retain their source columns in both
plain and colored excerpts. A change preview is shown only when the complete edit fits
on the displayed source line; otherwise the explanation and help remain available.

Checks run after Kotlin's frontend analysis. Kotlin may therefore report an ordinary
type error before Canopy can explain the declaration. Source locations support navigation
from build output; this does not add live IDE underlines or automatic quick fixes.

## Consumer setup

Apply the plugin to every Kotlin game module declaring custom nodes.
The example uses the checked-in source version; publish matching artifacts locally
until that version is available from your configured repository.

```kotlin
plugins {
    kotlin("jvm") version "2.4.10"
    id("io.github.canopyengine.compiler") version "0.1.0-dev4"
}
```

For source-built snapshots, configure `mavenLocal()` in pluginManagement repositories
alongside Gradle Plugin Portal and Maven Central. From the engine root, run
`gradlew.bat publishToMavenLocal` on Windows, or `./gradlew publishToMavenLocal` elsewhere.
This publishes the marker, both tooling artifacts and enabled engine modules.
The marker resolves the Gradle integration, which automatically supplies the
compiler artifact to all main and test compilations. A library dependency cannot
apply the plugin to another build. Use the Kotlin version pinned in the matching
engine version catalog; incompatible versions fail configuration.

Automatic storage needs no annotation and applies to direct and indirect custom
`Node` subclasses. Ordinary instance properties become guarded slots in `NodeState`:

```kotlin
class Enemy(name: String, val initialHealth: Int = 100) : Node<Enemy>(name) {
    var health = initialHealth
    var status by signal("idle")
    val alive get() = health > 0
}
```

The transformation removes physical backing fields and rewrites their reads and
writes, including custom accessors using `field`. It preserves constructor
parameters, initializer evaluation once in declaration order, init blocks,
inheritance, generic values and source property visibility/mutability. Slots are
qualified by their declaring class, so same-name base/subclass properties stay
independent. Reads before a subclass initializer (for example, a superclass
calling an overridden getter) receive the original JVM zero/null default.

Existing `NodeProperty`, `NodeDependency`, `GlobalDependency` and `AssetDelegate`
delegates are unchanged. Computed properties without backing fields and ordinary
companion/object/static state remain unchanged. Instance fields of a custom singleton extending
`Node` still undergo storage transformation/validation; static fields remain outside
that instance-storage guarantee. Unsupported `lateinit`, value-class
backing storage, field annotations (including `@JvmField`, `@Volatile` and
`@Transient`), third-party delegates and captured outer references fail with
source-located diagnostics. Use ordinary nullable state or explicit
`by nodeProperty(...)` where applicable; inner nodes must become nested classes.

Automatic storage changes storage only. It does not make ordinary values
reactive, attach Signal/Effect ownership, or dispose arbitrary resources.
Explicit owners, ownership scopes and existing lifetime registrations retain
their meaning. Access uses the same lifecycle guards as `nodeProperty`: detachment
preserves state; destruction invalidates access and clears engine-owned payload.
A separate construction pass protects supported constructor calls and releases
partially constructed nodes on failure; see [Failed node construction](#failed-node-construction).

Physical-field reflection, Java field access and serializers that depend on
backing fields must migrate to property accessors or explicit serialization.
Recompile consumers when adopting this feature; a previous public field ABI is
not preserved. Getter/setter names and Kotlin source property types remain the
same. Untransformed Java/precompiled consumers still undergo strict runtime
field validation: there is no trusted marker or validation exemption. Missing
runtime helper ABI fails compilation with `CANOPY_NODE_PROPERTY_ABI`; upgrade
compiler and engine together. Incompatible Kotlin versions still fail plugin
configuration.

## Failed node construction

An internal transform runs after mandatory validation and protects source constructor
calls to direct and indirect Node subclasses with the matching inline runtime
`nodeConstruction` boundary. Arguments evaluate once, in their original order, inside
that boundary; delegating superclass constructor calls are not wrapped. This applies
to consumer main and test compilations without annotations.

Constructor references receive `CANOPY_NODE_CONSTRUCTION_REFERENCE`: use an explicit
lambda calling the constructor instead. Suspending argument evaluation receives
`CANOPY_NODE_CONSTRUCTION_SUSPEND`; evaluate suspend values before construction.
Stored suspend callbacks are accepted because creating them does not suspend.
Construction boundaries are synchronous and confined to the calling thread. Java,
reflection, precompiled factories, first access to a named Node singleton and builds
without the plugin need an explicit
runtime boundary around their factory invocation. The compiler and runtime must both
provide this version's inline boundary; incompatible runtimes produce
`CANOPY_NODE_CONSTRUCTION_ABI` at actual node constructor calls.

## Verification

From the engine root run:

```text
gradlew.bat projects :compiler:projects test ktlintCheck build coverageReport --no-daemon --console=plain
```

Root tasks include both source sets and their tests/coverage. Tests check packaged
JAR isolation, the real generated Maven marker and an independent consumer's main
and test compilations, alongside compiler declaration and rule-provider fixtures.
Test-only publication writes into the module's build directory, not Maven Local.

## Adding a rule

Use `io.canopy.tooling.compiler.rules`. A rule declares its identity, validation phase,
target and diagnostics. A provider supplies rules; it does not install compiler passes.
For example, a studio can reserve a class name without receiving mutable compiler IR:

```kotlin
class ReservedNameRule : ClassRule {
    override val id = "studio.naming.reserved"
    override val phase = ValidationPhase.Source
    private val reserved = DiagnosticSpec("STUDIO_RESERVED_NAME", DiagnosticSeverity.Warning)
    override val diagnostics = setOf(reserved)

    override fun check(declaration: ClassInfo, context: RuleContext) {
        if (declaration.isNodeSubclass && declaration.name == "Reserved") {
            context.report(reserved, declaration.span,
                "Choose a different Node name", help = "Use a name describing its role in the game.")
        }
    }
}

class StudioRules : RuleProvider {
    override val id = "studio.rules"
    override fun rules() = listOf(ReservedNameRule())
}
```

Build the provider with the pinned Kotlin version and a compiler-only API dependency:

```kotlin
dependencies {
    compileOnly("io.github.canopyengine:canopy-compiler:0.1.0-dev4") {
        capabilities { requireCapability("io.github.canopyengine:canopy-compiler-checks") }
    }
}
```

The capability selects the compiler host variant, including in composite builds.
Do not apply Canopy's game compiler plugin to the provider itself. Add `StudioRules`' fully qualified name to
`src/main/resources/META-INF/services/io.canopy.tooling.compiler.rules.RuleProvider`.
The provider needs a public no-argument constructor. Install its independent JAR in the game:

```kotlin
dependencies {
    canopyCompilerRules("com.example:studio-canopy-rules:1.0.0")
}
```

`canopyCompilerRules` feeds compiler plugin classpaths, including test compilations;
it never adds the provider to the game's runtime dependencies. A file dependency works too:
`canopyCompilerRules(files("studio-rules.jar"))`. No registrar or traversal edits are needed.
The existing compiler artifact supplies the rule API; there is no extra SDK module.

`PropertyRule`, `ClassRule` and `DeclarationRule` select the target. Implement exactly
one per rule. `Source` runs before storage rewriting; `StoredState` runs afterwards.
Class storage/capture observations are populated in `StoredState` only. Facts and
source spans contain no IR or compiler services and must not be retained between
compilations. Collections supplied by the compiler are immutable. Rules can report
only their declared diagnostics at source spans from the current compilation.

Registration checks provider API revisions, duplicate identities, supported targets
and diagnostic ownership before reading declarations. Diagnostic severity is separate
from the rule ID. Providers cannot redefine mandatory Canopy diagnostics or downgrade
them to warnings. Metadata is copied once, and rules run in deterministic ID order
inside each phase. Every applicable rule completes that phase; errors then prevent
dependent transformations, while warnings allow them. A failed provider reports a
build error once per phase without preventing independent checks in that phase.

Storage, ownership, UI capture and construction remain trusted, explicitly ordered
transformations. Their existing runtime ABI checks stay conditional on actual use.
Checks that require transformation-local analysis remain beside that transformation;
there is no general transformation plugin SDK or automatic dependency graph.
The formatter presents diagnostics; it does not decide which validation phases run.
Tests cover registration conflicts, error/warning behavior, real independent JAR loading,
mandatory checks alongside providers, and the existing transformation interactions.
Node and UI compilation fixtures enable Kotlin's `-Xverify-ir=error` to catch invalid
IR links after the pipeline, alongside executing generated code.

The earlier `CanopyCompilerRule` service remains a **trusted compatibility API**, running
in `StoredState`. It exposes Kotlin IR and must not modify it. New providers should use
the read-only API above. Neither API is a security sandbox for arbitrary JVM code.
Rule API revision 1 is explicitly checked, but providers should still target the same
Canopy release and pinned Kotlin version; no cross-release binary compatibility is promised.
Compiler validation cannot prove that arbitrary third-party semantic policies agree.

## Maven namespace migration

Maven artifacts now use `io.github.canopyengine`, and the Gradle plugin ID is
`io.github.canopyengine.compiler`. Replace the former `io.canopy` dependency
group and `io.canopy.compiler` plugin ID together, then rebuild consumers. Kotlin
packages and imports remain `io.canopy.*`; source code does not need an import
migration. Old coordinates are not aliases or relocation publications.

The plugin marker is
`io.github.canopyengine.compiler:io.github.canopyengine.compiler.gradle.plugin`,
which stays within the verified `io.github.canopyengine` namespace. Changing
coordinates prepares publication; this change does not publish to Maven Central.
Until a release is published, build the matching artifacts with
`./gradlew publishToMavenLocal` and include `mavenLocal()` in dependency and
plugin-management repositories.
