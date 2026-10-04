# Canopy node compiler checks

Apply the Canopy plugin in every game module that declares custom nodes. Applying it
in an engine build does not enable it in a separately built game. A Maven dependency
cannot install a Gradle compiler plugin automatically.

```kotlin
plugins {
    kotlin("jvm") version "2.4.10"
    id("io.canopy.node-state") version "0.1.0-dev2"
}

repositories {
    mavenCentral()
    // Add the repository containing the matching Canopy development artifacts.
}

dependencies {
    implementation("io.canopy:engine:0.1.0-dev2")
}
```

Configure the repository containing the Canopy Gradle plugin in `pluginManagement`
in `settings.gradle.kts` too. The plugin marker, implementation and compiler artifact
must be published together; this local change has not been published yet. Use the
Kotlin version pinned by the matching Canopy release because Kotlin compiler plugin
APIs are version-specific. The plugin adds the compiler artifact automatically to
all Kotlin compilations in the module, including tests. No per-node annotation is
needed. Apply the plugin to each module declaring custom node subclasses.

```kotlin
class EnemyNode(name: String) : Node<EnemyNode>(name) {
    var health by nodeProperty(100)
}
```

An ordinary `var health = 100`, constructor property, immutable instance field,
`lateinit`, `lazy` or arbitrary delegate fails with `CANOPY_UNMANAGED_NODE_STATE`.
Computed properties and companion state are permitted. Direct and indirect subclasses
are checked. Engine-managed delegates guard access and release their values during
node destruction. The uppercase class-named DSL remains available.

Java and precompiled subclasses, and projects missing compiler integration, are
validated again before engine state allocation. Unsafe classes throw
`InvalidNodeDefinitionException`. Runtime validation does not provide a substitute
for the compile-time feedback required in the supported game project setup.

Compiler implementation and rule extension instructions live in
[`engine/compiler`](../../engine/compiler/README.md). This module supplies Gradle
integration and automatically resolves `io.canopy:engine-compiler`.
