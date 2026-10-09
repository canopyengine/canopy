# Canopy Engine

<p align="center"><img src="logo.png" width="420" alt="Canopy Engine logo"></p>

<p align="center">
  <img src="https://img.shields.io/badge/version-0.1.0--dev2-red.svg" alt="Canopy version 0.1.0-dev2">
  <img src="https://img.shields.io/badge/kotlin-2.4.10-blue.svg" alt="Kotlin version 2.4.10">
  <img src="https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-green.svg" alt="License: MIT or Apache 2.0">
</p>

**Canopy 0.1.0-dev2** is an experimental Kotlin/JVM engine built around node
trees, composable behaviors and reactive state. This is a development snapshot;
public APIs may change before stable 0.1.0. This README describes the combined
implementation proposed in [PR #208](https://github.com/canopyengine/canopy/pull/208),
not a published stable release.

## Current capabilities

- Node hierarchy, paths, groups, scene replacement and phase-ordered tree systems.
- Node behaviors, context providers, typed managers and application injection.
- Events, signals, computed values and synchronous effects.
- Immutable Vector2 values and 2D transforms.
- Shared frame/physics lifecycle, pause/resume and shutdown handles.
- Backend-neutral declarative Row/Column/Box layouts, Text, Button, keyed
  structural updates and shared focus; terminal measurement and rendering.
- Node visibility, compiler-managed property storage and failed-construction rollback.
- Adaptive terminal geometry and a bottom command overlay that captures gameplay
  input while simulation continues; pause/resume remains explicit by default.
- Interactive terminal hosting with queued keyboard input, plus a separate
  LibGDX headless host.
- Backend-neutral file handles, JSON/TOML codecs, ID registries and modular saves.
- Structured logging, ktlint, CodeQL and aggregate coverage reporting.

Desktop sources are present but excluded from the build. The enabled platforms
do not currently provide a supported graphical sprite or collision workflow.
The minimum declarative UI is available on terminal; no supported desktop UI
backend is claimed.
Fixed physics callbacks do not themselves supply a physics simulation.
There is no supported `canopy new` CLI. The ecosystem demo remains a scaffold;
its agreed 0.1.0 target uses rabbits and foxes with nine abstract day phases.

## Build from source

Use **JDK25** for the engine, **JDK17** for the compiler/Gradle tooling, the
checked-in **Gradle9.8.0** wrapper and **Kotlin2.4.10**.

```sh
./gradlew assemble
./gradlew ktlintCheck
./gradlew publishToMavenLocal
```

On Windows use `gradlew.bat`. For contributors, the full verification commands
are `./gradlew test ktlintCheck build coverageReport`. The aggregate coverage
gate is 60%. Desktop is excluded from these commands.

Enabled modules are `:engine`, `:adapters:libgdx`, `:adapters:mordant`, `:adapters:logback`,
`:platforms:headless`, `:platforms:terminal`, `:tooling:utils`, and
`:tooling:devtools`. `tooling/compiler` is an included build. Core, data,
input, commands and shared UI are packages in `:engine`.

## Use the snapshot

Publish locally first, then configure a terminal application. Apply the matching
[compiler plugin](tooling/compiler/README.md) to Kotlin modules declaring custom
nodes or using reactive declarative UI:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
dependencies {
    implementation("io.github.canopyengine:engine:0.1.0-dev2")
    implementation("io.github.canopyengine:platforms-terminal:0.1.0-dev2")
}
kotlin { jvmToolchain(25) }
```

For headless hosting use `io.github.canopyengine:platforms-headless:0.1.0-dev2` instead.
The headless host does not supply terminal input or filesystem asset services.
These instructions do not assume the snapshot is published to Maven Central.
When migrating an existing project, replace the `io.canopy` Maven group with
`io.github.canopyengine` and the `io.canopy.compiler` Gradle plugin ID with
`io.github.canopyengine.compiler`. Kotlin packages/imports remain unchanged.
See the [compiler migration notes](tooling/compiler/README.md#maven-namespace-migration).

```kotlin
import io.canopy.engine.app.Screen
import io.canopy.engine.app.screens
import io.canopy.engine.core.nodes.behavior
import io.canopy.engine.core.nodes.types.empty.EmptyNode2D
import io.canopy.engine.math.Vector2
import io.canopy.platforms.terminal.app.terminalApp

class ExampleScreen : Screen() {
    override fun onEnter() {
        EmptyNode2D("Root") {
            EmptyNode2D("Moving") {
                behavior(onUpdate = { delta ->
                    position = position + Vector2(delta, 0f)
                })
            }
        }.asSceneRoot()
    }
}

fun main() = terminalApp {
    screens { start(ExampleScreen()) }
}.launch()
```

This updates a transform; it does not render a sprite. Use TerminalApp's
`renderFrame(lines)` for text output. Terminal input is installed by the host.
The [first-project manual](https://github.com/canopyengine/canopy-docs/blob/main/markdown/manuals/getting-started/first-project.md)
includes the application plugin, entry point and JVM options.

Vector arithmetic returns immutable values; assign results back to node
properties. Signal reads use `state()` and writes use `state.update { ... }`.
Scene trees, manager registries and reactive updates expect serialized engine
thread access. Event callbacks are weakly referenced. Prefer node-owned subscriptions
and effects, or explicitly disconnect/dispose shared lifetimes. Construction rollback
is synchronous; it does not roll back arbitrary mutations to existing objects.

## Documentation and contributions

- [Documentation index](https://github.com/canopyengine/canopy-docs/blob/main/markdown/index.md)
- [Architecture](https://github.com/canopyengine/canopy-docs/blob/main/markdown/engine-details/engine-architecture.md)
- [Snapshot notes](https://github.com/canopyengine/canopy-docs/blob/main/markdown/misc/releases/0.1.0.md)
- [Roadmap](https://github.com/canopyengine/canopy-docs/blob/main/markdown/misc/roadmap.md)
- [Demos](https://github.com/canopyengine/canopy-demos)
- [Contribution guidelines](CONTRIBUTING.md) and [agent rules](AGENTS.md)

Main requires a PR, one approving review, required checks and squash merging.
Dependency updates are staged on `dependency-updates`; integration PRs to main
remain human-reviewed. See the workflows under `.github/workflows` for details.
Agent contributions disclose origin in branches, commits, PR titles and labels.

## License

Dual-licensed under [MIT](LICENSE-MIT) or [Apache 2.0](LICENSE-APACHE), at your option.
