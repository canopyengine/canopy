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

The registrar installs four ordered Kotlin IR passes before JVM bytecode generation:

1. **Node storage:** supported instance properties become guarded NodeState slots.
2. **Validation:** mandatory NodeState checks and rule providers inspect declarations.
3. **UI expression capture:** supported declarative expressions become reevaluable
   bindings and structural regions.
4. **Construction protection:** supported Node constructor calls receive synchronous
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

## Consumer setup

Apply the plugin to every Kotlin game module declaring custom nodes.
The example uses this source's proposed alpha.2 version; publish it locally until
it is available on Central. For the published alpha.1 runtime, use the matching
alpha.1 plugin instead.

```kotlin
plugins {
    kotlin("jvm") version "2.4.10"
    id("io.github.canopyengine.compiler") version "0.1.0-alpha.2"
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
    val status = signal(this, "idle")
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

Implement `CanopyCompilerRule` with a public no-argument constructor and a unique
`id`. `check` receives every source declaration, including classes, properties and functions. Use
a declaration type check and `context.isNodeSubclass` when a rule applies only to custom nodes, and
`context.report(id, declaration, explanation)` for source-located errors. The shared
context caches Node symbol resolution for the compilation. The internal storage transformation runs before mandatory checks and providers. Rules validate IR;
they must not transform it or retain compilation objects between builds.

```kotlin
/** Checks a reserved name on custom node classes. */
class NamingRule : CanopyCompilerRule {
    override val id = "CANOPY_RESERVED_NODE_NAME"

    override fun check(declaration: IrDeclaration, context: CanopyRuleContext) {
        if (declaration is IrClass && context.isNodeSubclass(declaration) && declaration.name.asString() == "Reserved") {
            context.report(id, declaration, "Choose a different node class name")
        }
    }
}
```

Add the implementation's fully qualified name, one per line, to
`src/compiler/resources/META-INF/services/io.canopy.tooling.compiler.CanopyCompilerRule`.
The registrar discovers these providers automatically. Providers must be visible
to the Canopy compiler plugin classloader. Registering
additional source rules in this module requires only their implementation and the
service entry; separately loaded Kotlin plugin jars do not automatically share
rule providers.
Duplicate IDs fail plugin initialization, rather than silently replacing a rule.
`NodeStateRule` is always installed and cannot be disabled or replaced by providers.
There is no application opt-out from the cleanup storage guarantee.

The SPI depends on Kotlin compiler IR APIs and must be built against the pinned
Kotlin version; it is not a version-independent binary API. Consumer compilation
fixtures verify supported automatic properties, unsupported declarations, payload-free JVM classes,
managed delegates and an independent provider
running alongside the mandatory rule.

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
