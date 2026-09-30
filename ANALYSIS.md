# Canopy Engine — Findings & Improvement Plan

> Analysis date: 2026-09-30 | Kotlin 2.4.10 | Java 25 (Corretto 25.0.4.1) | Gradle 9.8.0

---

## Overall Health Score

| Metric | Value | Verdict |
|---|---|---|
| Test pass rate | 80 / 80 ✅ | All green |
| **Total line coverage** | **655 / 2332 = 28%** | 🔴 Very low |
| Total instruction coverage | 3688 / 14357 = 25.7% | 🔴 Very low |
| Total branch coverage | 159 / 786 = 20.2% | 🔴 Very low |
| Source files (main) | 100 | — |
| Source files (test) | 20 | — |
| Test-to-source file ratio | 0.20 | 🔴 Thin |
| ktlint violations | 0 (active modules) | ✅ |
| Critical bugs found | 4 | 🔴 |
| Medium smells found | 7 | 🟡 |

---

## Are 80 Tests Reasonable?

**Short answer: No — the suite is significantly underpowered for a 100-file production codebase.**

### The numbers

- **100 production `.kt` files**, **20 test files** → 0.2 test-file ratio.
  Industry healthy baseline is typically **0.5–1.0+** (one test file per source file, or close).
- **80 test cases** for 2,332 testable lines → one test case per ~29 lines.
  A well-tested codebase targets one case per 5–15 lines of production logic.

### What _is_ covered well

The **reactive core** (`Signal`, `Computed`, `Effect`, `Event`, `TrackingContext`) has solid unit test coverage (~65–70% lines) and the 39 tests there are high quality — they test edge cases like dynamic dependency re-tracking, effect disposal, and context scoping. Similarly, `NodeTests`, `SaveManagerTests`, TOML/JSON parser tests are reasonable.

### What is _not_ covered at all

These packages have **literally zero test coverage**:

| Package | Production Lines | Tests |
|---|---|---|
| `app` (App, Screen, ScreenManager…) | 207 | 0 |
| `input` (InputManager, InputMapper, InputSystem) | 130 | 0 |
| `input.binds` | 255 | 0 |
| `input.events` | 31 | 0 |
| `data.assets` | 16 | 0 |
| `data.registry` (IdRegistry) | 42 | 0 |
| `logging.logback` | 34 | 0 |
| `core` (CanopyBuildInfo) | 11 | 0 |
| `platforms:terminal` (nearly all) | 132 | ~2 lines |
| `tooling:devtools` (nearly all) | 54 | ~4 lines |

These include the engine's **entire lifecycle**, **input handling**, **asset loading**, and **platform layer** — all things that can silently break without tests catching it.

### Verdict

> The 80 tests are a **solid foundation for the reactive subsystem**, but they provide **false confidence at the project level**. A bug introduced in `App.enter()`, `SceneManager`, `InputManager`, or any platform adapter would ship undetected. To be considered production-ready, the suite needs at minimum **150–200 tests** and a **line coverage target of ≥ 60%**.

---

## Coverage by Module

| Module | Instructions | Branches | Lines |
|---|---|---|---|
| `engine` | 27.7% | 22.2% | 30.2% |
| `platforms:terminal` | 0.9% | 0% | 1.5% |
| `tooling:devtools` | 8.0% | 0% | 7.4% |
| **TOTAL** | **25.7%** | **20.2%** | **28.1%** |

### Engine breakdown (by package)

| Package | Line Coverage | Grade |
|---|---|---|
| `core.nodes.types.empty` | 100% | ✅ |
| `data.saving` | 76% | 🟡 |
| `logging.slf4j` | 73% | 🟡 |
| `core.flows.events` | 70% | 🟡 |
| `core.flows` | 64% | 🟡 |
| `core.nodes` | 53% | 🟡 |
| `data.parsers` | 46% | 🔴 |
| `core.managers` | 37% | 🔴 |
| `logging` | 19% | 🔴 |
| `logging.util` | 15% | 🔴 |
| `math` | 14% | 🔴 |
| `app` | **0%** | ❌ |
| `data.assets` | **0%** | ❌ |
| `data.registry` | **0%** | ❌ |
| `input` | **0%** | ❌ |
| `input.binds` | **0%** | ❌ |
| `input.events` | **0%** | ❌ |
| `logging.logback` | **0%** | ❌ |

---

## Findings Catalogue

### Bugs (code that is likely wrong today)

| ID | Severity | File | Issue |
|---|---|---|---|
| BUG-1 | 🔴 HIGH | `Signal.kt` | `runBlocking` in `update()` — can deadlock & hitches game loop |
| BUG-2 | 🔴 HIGH | `TreeSystem.kt` | `createTreeSystem` discards its result — the helper is a no-op |
| BUG-3 | 🔴 HIGH | `SceneManager.kt` | `currScene` setter has no teardown — old scene tree leaks |
| BUG-4 | 🔴 HIGH | `SceneManager.kt` | Physics accumulator only runs 1 step/frame — physics desync under load |

### Code Smells (design issues that will hurt later)

| ID | Severity | File | Issue |
|---|---|---|---|
| SMELL-1 | 🔴 HIGH | `Signal.kt` | `value` field not `@Volatile` — data race risk |
| SMELL-2 | 🔴 HIGH | `Behavior.kt` | Single behavior, silently overwritten, no `onExitTree` on replace |
| SMELL-3 | 🟡 MED | `Effect.kt` | No re-entrancy guard — circular reactive graph → stack overflow |
| SMELL-4 | 🟡 MED | `Node.kt` | `_children` iterated without snapshot — `ConcurrentModificationException` if children added in `onUpdate` |
| SMELL-5 | 🟡 MED | `ManagersRegistry.kt` | Non-concurrent `LinkedHashMap` used across threads |
| SMELL-6 | 🟡 MED | `Node2D.kt` | `globalPosition/Scale/Rotation` are O(depth) uncached |
| SMELL-7 | 🟡 MED | `SaveManager.kt` | `loadData` throws `NoSuchElementException` on missing data |
| SMELL-8 | 🟡 MED | `Event.kt` | `log` is `public val` — internal detail leaks into sealed class API |
| SMELL-9 | 🟢 LOW | `CanopyBuildInfo.kt` | `JarFile` open on error poisons the `lazy` delegate |
| SMELL-10 | 🟢 LOW | `settings.gradle.kts` | Duplicate `include("adapters:mordant")` on line 50 |
| SMELL-11 | 🟢 LOW | `platforms/desktop/` | 32 ktlint violations (module currently disabled) |

---

## Prioritised Fix Plan

> Effort key: **S** = < 1h · **M** = half-day · **L** = full day · **XL** = multiple days

---

### P0 — Bugs: Fix Before Any Release

---

#### [P0-1] Replace `runBlocking` in `Signal.update` with `tryEmit`

**File:** `engine/src/main/kotlin/io/canopy/engine/core/flows/events/Signal.kt`
**Effort:** S

**Problem:** `runBlocking { flow.emit(new) }` is called from the game loop on every signal mutation. If any coroutine collector back-pressures or the same thread is in a coroutine context, this deadlocks. It also adds unnecessary thread-blocking overhead on the hot path.

**Fix:**
```kotlin
// Before
runBlocking { flow.emit(new) }

// After — safe because flow has replay = 1 (buffer always accepts)
flow.tryEmit(new)
```

**Acceptance criteria:** Signal updates are non-blocking. Existing `SignalTests` still pass. Add a test that updates a signal from inside a `runBlocking` coroutine scope without deadlock.

---

#### [P0-2] Fix `createTreeSystem` — it discards its result

**File:** `engine/src/main/kotlin/io/canopy/engine/core/nodes/TreeSystem.kt`
**Effort:** S

**Problem:** The function creates an anonymous `TreeSystem` object and returns `Unit`. The object is immediately GC'd. Any call to `createTreeSystem` today does nothing.

**Fix:** Return the created `TreeSystem` so callers can register it:
```kotlin
fun createTreeSystem(...): TreeSystem {
    return object : TreeSystem(...) { ... }
}
```

**Acceptance criteria:** `createTreeSystem` returns a usable, registerable `TreeSystem`. Add a test verifying systems created via the helper are invoked during tick.

---

#### [P0-3] Add scene teardown on `currScene` replacement

**File:** `engine/src/main/kotlin/io/canopy/engine/core/managers/SceneManager.kt`
**Effort:** M

**Problem:** `asSceneRoot()` sets `sceneManager.currScene = this` directly. The old scene's `nodeExitTree()` is never called, so behaviours, group memberships, and system node lists all leak.

**Fix:**
```kotlin
var currScene: Node<*>? = null
    set(newScene) {
        field?.let { old ->
            old.nodeExitTree()
            unregisterNodeTree(old) // remove from systems + groups
        }
        field = newScene
        newScene?.buildTree()
    }
```

**Acceptance criteria:** Switching scenes calls `onExitTree` on all old-scene behaviors. Group and system membership is empty after old scene teardown. Add integration test covering scene switch.

---

#### [P0-4] Fix physics accumulator to support multiple steps per frame

**File:** `engine/src/main/kotlin/io/canopy/engine/core/managers/SceneManager.kt`
**Effort:** S

**Problem:** `isPhysicsFrame(delta)` only drains a single `physicsStep` per call. Under load (slow frames), physics falls behind instead of catching up.

**Fix:**
```kotlin
physicsAccumulator += delta
val maxSteps = 5 // cap to prevent spiral-of-death
var steps = 0
while (physicsAccumulator >= physicsStep && steps < maxSteps) {
    physicsAccumulator -= physicsStep
    steps++
    systems[PhysicsPre]?.forEach { it.tick(physicsStep) }
    root.nodePhysicsUpdate(physicsStep)
    systems[PhysicsPost]?.forEach { it.tick(physicsStep) }
}
```

**Acceptance criteria:** A simulated 3× slow frame runs exactly 3 physics steps. Add a unit test on `SceneManager` physics tick count.

---

### P1 — Critical Smells: Fix Before Growing the Codebase

---

#### [P1-1] Add `@Volatile` to `Signal.value`

**File:** `Signal.kt` · **Effort:** S

```kotlin
@Volatile private var value: T = initial
```

If signals are ever read/written from multiple threads (coroutine collectors, background loading), this prevents stale-value visibility bugs. If single-threaded use is guaranteed, document that invariant explicitly with a comment or `@NotThreadSafe`.

---

#### [P1-2] Guard `Effect` against re-entrancy

**File:** `Effect.kt` · **Effort:** M

**Problem:** Effect block mutates signal → signal fires listener → re-enters `Effect.run()` → stack overflow for circular graphs.

**Fix:**
```kotlin
@Volatile private var running = false

private fun run() {
    if (disposed || running) return
    running = true
    val frame = TrackingContext.push()
    try { block() }
    finally {
        running = false
        TrackingContext.pop()
        updateDependencies(frame)
    }
}
```

**Acceptance criteria:** A circular `effect { signal.update { it + 1 } }` terminates after one run without stack overflow. Add a test for this.

---

#### [P1-3] Snapshot `_children` before iterating in lifecycle hooks

**File:** `Node.kt` · **Effort:** S

Replace all `children.values.forEach { ... }` in `nodeUpdate`, `nodePhysicsUpdate`, `nodeExitTree`, `nodeInput`, `nodeReady` with:

```kotlin
children.values.toList().forEach { ... }
```

This ensures behaviors can safely add/remove children during lifecycle callbacks without `ConcurrentModificationException`.

---

#### [P1-4] Call `onExitTree` on behavior when replacing it

**File:** `Behavior.kt` · **Effort:** S

```kotlin
var behavior: Behavior<N>? = null
    set(new) {
        if (built) field?.onExitTree()
        field = new
        if (built) new?.onEnterTree()
    }
```

---

### P2 — Coverage: Add Tests for Zero-Coverage Critical Paths

> Target: **≥ 60% line coverage** across all modules.

| Issue | Package | Lines | Current | Target | Effort |
|---|---|---|---|---|---|
| P2-1 | `app` lifecycle | 207 | 0% | 60% | M |
| P2-2 | `input` state machine | ~416 | 0% | 70% | M |
| P2-3 | `SceneManager` (systems, groups, ticking) | 408 | 37% | 65% | L |
| P2-4 | `IdRegistry` | 42 | 0% | 90% | S |
| P2-5 | `Vector2` math | 22 | 14% | 95% | S |
| P2-6 | `platforms:terminal` bootstrap | 88 | 0% | 40% | M |

**P2-1 — App lifecycle tests**
Use the headless backend: `enter()` boots cleanly, `update()` increments `frameCount`, `isPaused` blocks gameplay delta, `exit()` tears down all managers.

**P2-2 — InputManager state machine**
Test `JustPressed` → `Pressed` → `JustReleased` → `Released` transitions, `getAxis`/`getInputVector`, `processEvents()` draining the queue, and a `registerPersistence()` roundtrip.

**P2-3 — SceneManager**
`addSystem`/`removeSystem` round-trip, `signalGroup` broadcast, `onUpdate` system phase ordering, scene transition teardown (after P0-3).

---

### P3 — Polish: Low-Risk Improvements

| Issue | File | Fix | Effort |
|---|---|---|---|
| P3-1 | `Event.kt` | Make `log` `private` | S |
| P3-2 | `Node2D.kt` | Cache `globalPosition/Scale/Rotation` with dirty flag | M |
| P3-3 | `SaveManager.kt` | Replace `first {}` with `firstOrNull` + meaningful error | S |
| P3-4 | `settings.gradle.kts` | Remove duplicate `include("adapters:mordant")` on line 50 | S |
| P3-5 | `platforms/desktop/` | Run `ktlintFormat` to fix 32 violations | S |
| P3-6 | `CanopyBuildInfo.kt` | Wrap `JarFile` access in `try/catch` to prevent poisoned `lazy` | S |

---

## Execution Roadmap

```
Week 1 ──── P0 Bugs (~3 days)
            BUG-1  Signal.update runBlocking    [S] ─ ~30min
            BUG-2  createTreeSystem no-op       [S] ─ ~30min
            BUG-4  physics accumulator          [S] ─ ~1h
            BUG-3  scene teardown               [M] ─ ~4h

Week 2 ──── P1 Smells (~2 days)
            SMELL-1  @Volatile Signal.value     [S] ─ ~15min
            SMELL-4  children snapshot          [S] ─ ~30min
            SMELL-2  Behavior replace fix       [S] ─ ~1h
            SMELL-3  Effect re-entrancy guard   [M] ─ ~3h

Weeks 3–4 ─ P2 Coverage (target 60%, ~200 tests)
            P2-4   IdRegistry tests             [S]
            P2-5   Vector2 tests                [S]
            P2-1   App lifecycle tests          [M]
            P2-2   InputManager tests           [M]
            P2-6   Terminal bootstrap tests     [M]
            P2-3   SceneManager tests           [L]

Ongoing ─── P3 Polish (as bandwidth allows, ~1 day total)
```

---

## Coverage Targets

| Milestone | Line Coverage | Approx Tests |
|---|---|---|
| Today | 28% | 80 |
| After P0 | 28% | ~90 (+regression tests) |
| After P1 + P2 | ~60% | ~170–200 |
| Production-ready | ≥ 70% | ~220–250 |
