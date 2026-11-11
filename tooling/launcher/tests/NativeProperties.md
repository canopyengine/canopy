# Native property test pass

This is a testing reference for the developer-experience branch, not the Aquarium tutorial.
Use matching engine and compiler artifacts from this branch. Both still use `0.1.0-dev4`.

## The declaration rule

Use `by` for live Canopy properties. Read and assign them like ordinary Kotlin properties:

```kotlin
import io.canopy.engine.core.nodes.Node
import io.canopy.engine.core.queries.ancestor

class Aquarium : Node<Aquarium>("Aquarium") {
    var food by signal(10)
    val population by computed { getChildrenOfType<Fish>().size }
    private var nextFish = 0

    override fun nodeInit() {
        addFish()
    }

    fun addFish() {
        addChild(Fish("Fish${nextFish++}"))
    }
}

class Fish(name: String) : Node<Fish>(name) {
    val aquarium by ancestor<Aquarium>()
    var hunger by signal(100)
    val needsFood by computed { hunger < 20 && aquarium.food > 0 }
}
```

`population` follows child membership, including Fish subclasses. Reparenting changes
which aquarium a Fish resolves. Context wrappers are transparent to typed child queries;
`children` still describes physical direct children. `hasExactChildType` deliberately
excludes subclasses.

Use `=` for ordinary Kotlin values, snapshots and function results. Immediate lookups
are named `getManager`, `getContext`, `getChild` and their `OrNull` counterparts.
`manager`, `context`, `child`, `ancestor`, `tree` and `group` are live delegates from
`core.queries`. Required lookups throw when missing; `OrNull` does not hide invalid
owners or incompatible types. The older immediate/cached helpers remain deprecated
compatibility APIs.

## Collections follow Kotlin mutability

```kotlin
var names by signal(mutableListOf("Bubbles"))
val population by computed { names.size }
names.add("Pebble")
names.removeAt(0)
names = mutableListOf("Coral")

var messages by signal(listOf("Welcome"))
messages = messages + "Fish added"
```

Lists, sets and maps use the same `signal` factory. Mutable interfaces observe edits
through iterators, sublists and map views too. Inputs and replacements are shallow
copies: editing the original collection does not edit stored state. An escaped view
of a replaced collection no longer changes or notifies its former source. Collection
Flow values are live views, not historical snapshots.

Reactive fields inside an element are tracked when read by computed/effect. Plain
fields are not deeply observed. Explicit `Signal(...)`, generic scalar wrappers and
caller-owned low-level integrations keep their scalar semantics; prefer inferred
`signal(mutableListOf<Fish>())` for mutable collection state.

## Lifecycle and observation

- Constructor arguments and configuration initialize once. Explicit `addChild` also
  works inside `nodeInit`; automatic composition does not attach the same child twice.
- Entry runs before readiness; readiness runs once per entry after readying children.
  Games override lifecycle hooks. App drives normal lifecycle; `buildTree` is a
  low-level standalone-driver entry point.
- Reusable removal retains state values but suspends reactive work and connections.
  Reentry renews observation. Destroying a node clears its stored payload.
- Create effects in `onEnterTree` when they must run on each entry. Cancel owned
  coroutine/Flow jobs on removal; collecting a Flow does not transfer job ownership.
- Hierarchy changes notify after a complete operation or construction rollback, with
  no frame polling. Structural tracking currently uses a coarse scene-wide revision;
  unrelated structural edits can reevaluate a derivation, and equal computed results
  suppress downstream changes. Keep derivations pure.

## Run the acceptance fixture

Publish matching dependencies into a separate local Maven directory:

```sh
./gradlew test ktlintCheck build coverageReport
./gradlew publishToMavenLocal -Dmaven.repo.local=/absolute/path/to/test-maven
```

In a disposable copy of the Aquarium starter, put
[TerminalAcceptance.kt](fixtures/TerminalAcceptance.kt) into `src/main/kotlin/Main.kt`.
Keep `MainKt` as the entry point. From that starter folder:

```sh
java CanopyLaunch.java --gradle-arg "-PcanopyRepository=file:///absolute/path/to/test-maven"
```

On Windows use `gradlew.bat` and a repository URI such as `file:///C:/canopy-test-maven`.
Use `--current-terminal` for SSH/headless testing. Check rabbit addition, computed
population, animated ticks, focus, mouse hover/scroll, command capture, resize,
pause/resume and exit. Automated regressions cover collection views, active reparent,
rollback, detach/reentry, destruction and input predicates.

The starter's Main.kt remains empty in source control. Game implementation, the public
manual/site, an IDE plugin and native Windows/macOS verification are separate work.
