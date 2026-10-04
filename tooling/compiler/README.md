# Canopy compiler tooling

`tooling/compiler` is one included-build module containing compiler checks and
Gradle integration. Both share the `io.canopy.tooling.compiler` package root;
Gradle-specific classes live in its `gradle` subpackage.

The module builds two isolated artifacts at the engine version:

| Source set | Artifact | Execution host |
| --- | --- | --- |
| `compiler` | `io.canopy:canopy-compiler` | Kotlin compiler |
| `main` | `io.canopy:canopy-compiler-gradle` | Gradle |

The compiler artifact contains its registrar, rule SPI, checks and compiler service
registration. The Gradle artifact contains only integration classes and the plugin
descriptor. Compiler APIs and Gradle APIs are provided by their respective hosts;
we do not bundle either host into the other artifact. The composite build selects
the compiler-specific capability when resolving the compiler dependency.

## Consumer setup

Apply the plugin to every Kotlin game module declaring custom nodes:

```kotlin
plugins {
    kotlin("jvm") version "2.4.10"
    id("io.canopy.compiler") version "0.1.0-dev2"
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

Checks need no per-class annotation and apply to indirect Node subclasses.
Unmanaged backing fields produce `CANOPY_UNMANAGED_NODE_STATE`; use engine-managed
`by nodeProperty(...)` storage instead. Runtime validation remains necessary for
Java, precompiled classes and builds missing this integration.

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
context caches Node symbol resolution for the compilation. Rules validate IR;
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
fixtures verify unsafe declarations, managed storage and an independent provider
running alongside the mandatory rule.
