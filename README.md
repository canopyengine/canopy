# Canopy Engine

<p align="center"><img src="logo.png" width="420" alt="Canopy Engine logo"></p>

**Canopy 0.1.0-dev2** is an experimental Kotlin/JVM engine built around node
trees, composable behaviors and reactive state. This is a development snapshot;
public APIs may change before stable 0.1.0.

## Current capabilities

- Node hierarchy, paths, groups, scene replacement and phase-ordered tree systems.
- Node behaviors, context providers, typed managers and application injection.
- Events, signals, computed values and synchronous effects.
- Immutable Vector2 values and 2D transforms.
- Shared frame/physics lifecycle, pause/resume and shutdown handles.
- Interactive terminal hosting with queued keyboard input, plus a separate
  LibGDX headless host.
- Backend-neutral file handles, JSON/TOML codecs, ID registries and modular saves.
- Structured logging, ktlint, CodeQL and aggregate coverage reporting.

Desktop sources are present but excluded from the build. The enabled platforms
do not currently provide a supported graphical sprite/UI/collision workflow.
Fixed physics callbacks do not themselves supply a physics simulation.
There is no supported `canopy new` CLI. The ecosystem demo remains a scaffold.

## Build from source

Use **JDK25**, the checked-in **Gradle9.8.0** wrapper and **Kotlin2.4.10**.

```sh
./gradlew assemble
./gradlew ktlintCheck
./gradlew publishToMavenLocal
```

On Windows use `gradlew.bat`. For contributors, the full verification commands
are `./gradlew test ktlintCheck build coverageReport`. The aggregate coverage
gate is 60%. Desktop is excluded from these commands.

Enabled modules are `:engine`, `:adapters:libgdx`, `:adapters:mordant`,
`:platforms:headless`, `:platforms:terminal`, `:tooling:utils`, and
`:tooling:devtools`. Core, data and input are packages in `:engine`.

## Use the snapshot

Publish locally first, then configure a terminal application:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
dependencies {
    implementation("io.canopy:engine:0.1.0-dev2")
    implementation("io.canopy:platforms-terminal:0.1.0-dev2")
}
kotlin { jvmToolchain(25) }
```

For headless hosting use `io.canopy:platforms-headless:0.1.0-dev2` instead.
The headless host does not supply terminal input or filesystem asset services.
These instructions do not assume the snapshot is published to Maven Central.

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
thread access. Event callbacks are weakly referenced; retain ownership and
disconnect subscriptions/dispose effects during cleanup.

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
