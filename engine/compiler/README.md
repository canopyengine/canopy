# Canopy compiler checks

The compiler implementation lives in `engine/compiler`. It is built once as
`:engine-compiler` inside the `compiler-gradle-plugin` included build, so the Gradle
integration is available while configuring the engine and independent game builds.
Its Maven coordinate is `io.canopy:engine-compiler`, using the engine version.
It is a compilation dependency, not an engine runtime dependency.

Consumers apply `io.canopy.compiler` to each Kotlin game module. The Gradle
integration installs the compiler artifact for all Kotlin compilations and enforces
the Kotlin version pinned in `gradle/libs.versions.toml`. Runtime validation remains
necessary for Java, precompiled classes and builds missing the integration.

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
`src/main/resources/META-INF/services/io.canopy.engine.compiler.CanopyCompilerRule`.
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
