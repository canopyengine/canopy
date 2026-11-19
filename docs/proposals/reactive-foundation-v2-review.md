# Reactive Foundation V2: architecture and feasibility review

Status: pre-implementation design review, captured against `bcbc07c` (2026-11-16).
The audit and plan below describe that baseline, not the current implementation.
The prototype status and remaining gaps are recorded after the verdict.
The lifecycle hook was renamed from `nodeInit` to `onSetup` after that baseline.

## Verdict

The API direction is coherent, but the acceptance criteria are not yet an
implementation specification. Canopy already has one signal-based dependency
tracker and a usable cached `Computed`; constructor expression capture and
read-only input storage are the missing pieces. A general `@Input val` is
plausible for a direct, plugin-compiled Kotlin constructor call, **not proven**
for default expressions, constructor delegation, cross-module metadata/ABI,
failed construction, or calls compiled without the plugin. Do not announce the
Aquarium example as supported until a compiler prototype and real engine
integration tests pass.

The smallest path is to harden the existing foundation, seed an internal
`Computed`-like derived value from a tracked construction-time evaluation, and
make the compiler supply that evaluation. Do not add a second subscription
engine, a public binding handle, automatic tree recomposition, or an extra
mutable signal mirroring each input.

## Prototype status (2026-11-18)

The implementation now captures direct primary-constructor `@Input val` expressions,
evaluates them once before construction, and installs a receiver-owned seeded
`Computed` after successful construction. Annotated getters use existing guarded
Node storage. The same path works for non-UI nodes and direct `Text`/`Button` labels.
Source revisions detect writes during capture/construction and subsequent
evaluations. Computed values, property connections, and UI observers settle
newly read sources that change before subscriptions are installed. Supported
observable collections propagate in-place edits and equal-content identity
switches through derived values and inputs.
The compiler prototype proves named-argument evaluation order and positive
cross-module construction when both modules apply the plugin. Focused compiler,
runtime input, property-connection, effect and UI tests pass. The full
`gradlew.bat projects test ktlintCheck build coverageReport --no-parallel
--console=plain` gate passed with a workspace `TEMP` directory: 9,540 of 10,525
lines covered (90.6%). An earlier full attempt hit an unrelated intermittent
Windows temp-file rename failure in `:adapters:logback:test`; its isolated retry
and the subsequent workspace-temp full gate passed. `git diff --check` passed.

The standalone reactive graph probe covers 1,000 and 10,000 computed, reactive
input and static input values. A local JDK 25 run observed zero idle expression
reevaluations and allocations; each 10,000-value fan-out update reevaluated
exactly 10,000 expressions. Input reads include guarded storage lookup while
the computed comparator reads directly, so the measured timings in the probe
README are not a like-for-like performance regression gate.

This is not full V2 acceptance. The following boundaries remain explicit:

- **Compiler participation:** A provider compiled without the plugin is diagnosed
  by a plugin-enabled consumer. A consumer compiled without the plugin cannot
  receive a compiler diagnostic and currently constructs a static value.
  Application startup already fails without the plugin in its launching module;
  the agreed build precondition is to apply it to every Kotlin module that
  constructs reactive inputs, including libraries.
- **Constructor forms:** Nonliteral defaults, dynamic superclass delegation,
  and secondary constructors on input owners are rejected. Literal omitted
  defaults and literal superclass arguments are static and do not provide a
  connectable derived source. Java and reflection calls cannot capture expressions.
- **Property compatibility:** Direct `Text`/`Button` named constructor arguments
  change from `text =` to `label =`; positional arguments and mutable `.text`
  remain. A manual `.text` assignment takes precedence over later label updates.
- **Scheduling:** Computed values and inputs notify synchronously, so diamond
  graphs can expose intermediate results. Declarative UI bindings keep their
  next-frame presentation scheduling. Effects have a per-effect feedback bound,
  not a graph-wide scheduler. Scalar Signal listener recursion is bounded at
  64 nested updates, which also rejects legitimate deeper synchronous chains;
  collection feedback is bounded at 1024 publications. Side-effectful UI
  bindings may still schedule a rerun on every frame. A graph-wide iterative
  scheduler and deep-chain benchmark remain separate follow-up work.
- **Lifetimes and threads:** Capturing an exited source fails; later source
  disconnection clears downstream listeners and makes computed/input reads
  fail while that source is unavailable. A read after recovery can rearm, but
  disconnected listeners must be recreated.
  Conditional consumers can switch away from an unavailable source without
  reading it. A never-entered source permits a construction snapshot but does
  not publish off-tree writes until entry, leaving an active receiver at that
  snapshot meanwhile. On reentry, seeded inputs resynchronize at the receiver's
  ready phase, after later siblings enter; an explicit read in an earlier
  `onEnterTree` can still fail while a later sibling source is inactive.
  Derived `untrack` reads of an exited source now fail;
  `untrack` only suppresses dependency registration, not lifetime validation.
  An active effect disposes on source disconnection; a UI binding reports an
  unavailable source at its next presentation frame and disposes. Receiver exit
  suspends input observation and reentry resynchronizes. Reactivity remains
  game-thread confined by contract, without thread-identity enforcement.
- **Scope:** The controlled Slider is explicitly deferred. No external
  `canopy-docs` update or historical before/after measurement is included.
  Generic property connections now suppress equal scalar results while
  retaining same-reference mutable value notifications.

These limits are not silently presented as complete safeguards. In particular,
the absence of a consumer compiler plugin cannot be repaired or diagnosed by a
compiler pass that never runs; a future ABI-level enforcement design would need
to distinguish compiler-captured calls from uncaptured calls without breaking
valid static defaults and superclass construction.

## Current contracts

| Area | Present behavior | Gap or conflict with V2 |
| --- | --- | --- |
| Signals | `Signal.kt` stores one value, suppresses equal assignments, publishes callbacks synchronously, creates Flow lazily, and adapts standard collections to owned observable copies. Detached node-owned signals retain values but stop publication; entry emits the latest value. | Scalar callback writes can recursively nest without a bound. A throwing callback leaves the new value installed but Flow replay at the last completed emission. `@Volatile` does not make update atomic; game-thread serialization is documented, not checked. `clear()` also clears internal dependency listeners. |
| Tracking | `TrackingContext.kt` uses thread-local nested frames and `untrack`; `DependencySubscriptions.kt` diffs signal sets and holds handles. | Reconciliation is not transactional if connecting a newly discovered source throws partway through. Computed/effect/UI subscriptions do not request disconnect notification, so a source exit/dispose/`clear()` can leave a live consumer silently stale. |
| Computed | `Computed.kt` is lazy, caches through an internal `Signal`, tracks dynamic dependencies, propagates equal-value suppression, and owns its source through `SourceLifetime`. It clears subscriptions and cache on node exit, computes detached reads without observation, and renews observation on a later read after reentry. | First-evaluation circular reads have no cycle guard. `recomputing` drops invalidations arriving during a recomputation rather than queuing a final pass. A failed reevaluation keeps the previous output and reconciles dependencies read before failure, but failure/read policy is not explicit. It has no seeded-initial-value path for inputs. |
| Effects | `Effect.kt` tracks conditional dependencies, runs immediately or at a new node's first entry, coalesces updates during a run, and clears subscriptions on disposal. Node-owned effects are removal-scoped. | A first-run write occurs before dependencies are installed and may be missed; later self-updates can loop indefinitely in the synchronous `do/while`. There is no bounded scheduler or deterministic loop diagnostic. |
| Connections | `PropertyConnections.kt` reads a bound property once without initial callback, tracks its signals, rejects an initially nonreactive getter, and queues reentrant reevaluations. `EventConnections.kt` permits listeners to disconnect during dispatch; node ownership releases handles. | An arbitrary reactive getter is notified on dependency invalidation even if its resulting value is equal; only a getter backed by `Computed` gets derived equality suppression. A getter that later yields zero dependencies fails during notification. Source disconnect ends a property connection; it does not automatically reconnect after source reentry. Preserve the current change-only initial policy. |
| Hierarchy | `SceneManager.kt` lazily creates a manager-wide hierarchy revision signal. `Node.kt` queries and `NodeDependency.kt` register it; physical add/remove/reparent/group changes publish after mutation boundaries. Existing tests cover several subclass, reparent, destruction and rollback paths. | Invalidation is intentionally coarse: unrelated structural edits can recompute every observed query. Entry-generation changes are distinct from revision publication; add explicit exit/reentry tests before asserting all lifecycle cases. `getChildrenOfType` creates a list on each read. |
| Lifetimes | `NodeLifetime.kt` supplies ambient ownership for effects/connections; `SourceLifetime.kt` guards signal/computed values, watches node activity, and disposes on destruction. `NodeConstruction.kt` rolls back plugin-protected failed constructors. `UiObserver.kt` uses the same tracker/subscription helper but has UI-specific next-frame scheduling. | These are different roles, not obviously redundant classes. Source and receiver lifetimes can diverge; the current graph has no explicit recovery policy when a captured source exits or dies while its consumer stays active. UI's deferred scheduler should not be replaced by synchronous input propagation without a decision. |
| Compiler | `CompilerPipeline.kt` has source validation, automatic Node property storage, ownership inference, selective `UiScope` expression capture, and constructor rollback wrapping. `NodePropertyTransform.kt` already removes supported Node backing fields while retaining Kotlin `val`/`var` accessors and initializer order. | No `@Input` annotation, input storage lowering, arbitrary constructor-argument capture, or input diagnostics exist. UI capture is limited to recognized DSL calls, not direct `Text` construction in the one-time setup hook. Existing compiler tests do not prove an annotated class built in module A and constructed reactively in module B. |

Relevant regressions already exist in `ComputedTests.kt`, `EffectTests.kt`,
`PropertyConnectionTests.kt`, `ReactiveHierarchyTests.kt`,
`ReactivePropertyTests.kt`, and `UiCompilerIntegrationTests.kt`. They prove
selected behavior, not the V2 safeguards above. The manual currently says
node-owned computed values are disposed on reusable exit, whereas current
`Computed.kt` suspends, clears its cache, and can be read/reentered; reconcile
that documentation before a public lifecycle promise.

## Compiler feasibility and blockers

1. **Declaration and storage.** `@Input val` can preserve a public read-only
   Kotlin property only if generated code supplies an internal writable path.
   Existing `NodePropertyTransform` is strong evidence that backed constructor
   `val` fields can be moved into guarded `NodeState` storage. It is **not** proof
   that a value can be replaced after construction while keeping all getter,
   inheritance, reflection, property-reference, generic, and initialization
   semantics. An input-specific lowering must run with, not after blindly
   duplicating, that storage pass. Initially the getter must return the single
   evaluated constructor value, including inside `init`; after installation it
   must read one owner-held derived source and register that source during
   tracking. Do not retain both a mirrored mutable value and `Computed` long
   term. Current storage already excludes value-class fields, `lateinit`, field
   annotations, and unsupported captured/inner Node forms.

2. **Call-site capture.** The existing `UiExpressionTransform` captures selected
   argument IR into lambdas, and `NodeConstructionTransform` wraps Node calls,
   including argument evaluation, in `nodeConstruction`. This is a precedent,
   not a general constructor implementation. A new pass must stage annotated
   arguments at their original evaluation positions, evaluate each initial
   expression once under tracking, construct the receiver, then install only
   after successful construction. It must preserve named-argument source order,
   ordinary arguments between inputs, generic/nullable types, side effects,
   non-local returns, and exception identity. It must clean temporary listeners
   if any argument or the constructor throws. If a source changes between evaluation and
   installation, a pending invalidation or post-commit resynchronization is
   necessary; the latter may legitimately perform a *later* reevaluation, so
   “initial expression once” must mean once for initial argument evaluation,
   not once over the object's entire construction transaction.

3. **Defaults and delegation.** An omitted default argument has no call-site
   expression to capture. A reactive default declared in another module cannot
   be implemented by merely wrapping the consumer's explicit arguments.
   Secondary constructor calls and superclass `this`/`super` delegation take
   different IR paths; the existing construction pass intentionally does not
   wrap delegating calls. Restrict the prototype to direct primary-constructor
   calls with explicit inputs, then prove or reject defaults, secondary calls,
   inheritance/overrides, constructor references, anonymous objects, and
   inline/suspend contexts individually. Static defaults alone may be a small
   first supported subset, but treating reactive defaults as static would
   contradict the proposal unless diagnosed and documented as unsupported.

4. **Cross-module and missing-plugin behavior.** The definition module needs
   annotation metadata plus generated storage/install ABI; every Kotlin
   consumer module needs the matching plugin to capture expressions. Root
   Gradle applies the plugin to enabled Kotlin subprojects, but external
   consumers must install it themselves. An IR plugin in module A cannot
   produce a compile diagnostic in module B if B has no plugin. Therefore the
   unconditional “no silent static fallback” goal requires a detectable
   runtime guard or a changed source/constructor ABI for missing-plugin calls,
   plus documentation of Java/reflection behavior. Neither has been proven.
   Test a published A artifact against B with and without the plugin, not only
   two source files in one compilation.

5. **Diagnostics.** The current plugin validates in IR after Kotlin frontend
   analysis. `@Input var`, a non-property parameter, unsupported owner, and
   known unsupported lowering are candidates for custom IR diagnostics. With
   `@Target(VALUE_PARAMETER)`, a property-body annotation is already invalid to
   Kotlin; requiring the exact `CANOPY_INPUT_INVALID_TARGET` code even there
   may require a FIR frontend checker or a broader annotation target, neither
   part of the small IR-only design. Dynamic cycles, loops, lifetime and thread
   failures are runtime errors, not compiler diagnostics. Compiler checks
   cannot prove arbitrary expression purity or whether a default/helper reads
   reactive state at runtime.

6. **Component compatibility.** Current `Text` and `Button` constructors accept
   plain `String` arguments and expose mutable `text`; `UiScope.Text/Button`
   have special compiler-generated `UiObserver` bindings. Thus
   `Text("Fish: $fishCount")` directly in the one-time setup hook was
   static. Making it live requires an explicit component API decision: migrate
   those constructors to read-only inputs (a public API change), or bridge new
   input values into existing mutable presentation properties without
   conflicting writers or duplicate long-lived state. The existing
   `UiScope` structural reconciliation remains separate; inputs should not
   recreate nodes or rerun that hook.

## Smallest architecture worth prototyping

```text
Signal and lazy hierarchy revision
          -> TrackingContext + DependencySubscriptions
          -> one cached derived-value implementation (existing Computed path)
          -> computed delegate OR compiler-generated input getter
          -> existing connect / effect observation

UiObserver continues to use the same dependency primitives, but keeps its
next-frame UI scheduling and structural reconciliation.
```

Prefer a private seeded-construction path through `Computed` (or a private
derived core extracted from it if the prototype proves necessary). The seed
contains initial value and discovered dependencies. The compiler-generated
input property holds one derived source in guarded Node state after successful
installation, with transient initial-value storage only during construction.
That source owns its receiver lifetime, output equality, tracking, and
notifications. For a seed with no dependencies, allocate no *upstream*
subscriptions. If static inputs must remain connectable, retain a read-only
output source; decide this explicitly rather than making static `connect`
silently fail.

Do not merge `NodeLifetime` and `SourceLifetime` just to reduce class count:
ambient registration and source validity/activity are different contracts.
First factor only duplicated cleanup or invalidation logic revealed by tests.
Keep `UiObserver`'s presentation queue because UI updates currently run at a
frame boundary, while computed/connection delivery is synchronous. A fully
glitch-free, topologically batched graph would be a larger behavior change; V2
must choose whether current synchronous intermediate notifications remain
allowed for diamond dependencies.

## Policies to decide before implementation

| Decision | Minimal recommended contract | Why it matters |
| --- | --- | --- |
| Failed derived evaluation | Preserve the last successful cache, propagate the failure, reconcile safely, and specify whether subsequent reads throw/retry until success. Never emit a value change for a failed evaluation. | Today's cached output can remain readable after a failed recomputation; V2 cannot leave an input silently stale without a stated policy. |
| Reentrancy and loops | Queue a dirty rerun for computed/input instead of dropping it; use one deterministic effect work queue with a bounded nonconvergence diagnostic, while allowing convergent transitions. | Current scalar signals can recurse and effects can spin. A fixed low rerun count alone would reject valid convergence. |
| Source exit/destruction/`clear()` | Detect dependency disconnection and define rearm or fail-fast semantics. Do not leave an active input bound to an unreachable source. | An active receiver can outlive a source. Source reentry currently does not reconnect an existing property connection. |
| Receiver entry | Resynchronize before an input is relied on in setup/entry hooks, or document the construction snapshot window. | `Node.nodeEnterTree()` runs one-time setup before activity observers resume. |
| Threading | Retain documented single serialized game-thread execution initially, with deterministic misuse tests/guards if V2 promises runtime rejection. Do not infer safety from `@Volatile` or the unrelated concurrent Event test. | Current maps, listeners, updates and tracking frames are not a thread-safe graph. |
| Equality and dispatch | Inputs should use `Computed`'s `old != new` output policy. Keep collection in-place edit notifications and `connect`'s no-initial-callback policy. State whether transient diamond values are acceptable. | Generic property connections currently notify on any tracked invalidation, not value equality. |
| Missing compiler | Require the plugin in both definition and consumer compilations and choose a runtime missing-install failure path. | A plugin cannot diagnose code in a compilation where it never ran. |

## Staged implementation plan and exit gates

1. **Contract freeze and baseline.** Agree on the policy table, supported
   constructor forms, public `Text/Button` migration, static-input observation,
   and a missing-plugin failure mode. Record existing behavior before changing
   it; distinguish deliberate compatibility changes from bug fixes.
2. **Foundation regressions.** Add focused engine tests for direct/mutual
   computed cycles; equal derived values; nested/reentrant signal and computed
   updates; effect first-run writes, convergent and nonconvergent feedback;
   mutation during listener dispatch; exception recovery; source `clear`, exit
   and destruction; receiver exit/reentry; and hierarchy membership changes.
   Fix only proven failures with the agreed semantics.
3. **Shared derived core.** Add pending invalidation/cycle and exception-state
   handling to the existing computed path; make subscription reconciliation
   transactional and lifetime-aware. Preserve existing public `computed`,
   `signal`, `effect` and `connect` signatures and their documented initial
   invocation behavior. Avoid a second reactive graph.
4. **Compiler proof of concept (stop/go gate).** In compiler tests, lower one
   `@Input val` on a direct Node primary constructor. Verify generated
   read-only Kotlin property, no unmanaged facade field, initializer order,
   exact initial evaluation count, updates, static input, named/mixed arguments,
   generic and nullable types, bound property references, failure cleanup, IR
   verification, and negative diagnostics. Publish an A fixture and compile/run
   B with/without plugin; test defaults and constructor delegation separately.
   If the property/constructor ABI cannot preserve Kotlin semantics, stop and
   revise the design rather than shipping a partial automatic feature.
5. **Runtime input integration.** Seed the same derived implementation, install
   only on successful construction, suspend and resume with receiver activity,
   and release closure/subscriptions on destruction. Test source/receiver
   lifetime mismatch and updates during construction explicitly.
6. **Components and examples.** Integrate chosen `Text/Button` semantics and
   verify the Pocket Aquarium fish counter updates while retaining label node
   identity. Then add one gameplay/lighting and one audio or physics adapter
   example; side effects use a connection or renderer read, not hidden tree
   recreation. Add a controlled Slider only after its API is independently
   approved.
7. **Performance and documentation.** Add a repeatable reactive benchmark
   alongside `tooling/benchmarks` with static, idle, fan-out, chain/diamond,
   dynamic dependency, hierarchy fan-out, and 1k/10k input workloads. Compare
   pre/post change on the same JDK and host, tracking allocations and
   evaluations, before adding fine-grained caches. Update the compiler guide,
   Canopy class reference/manual, and lifecycle/error contract. Run affected
   tests, then the repository's `gradlew.bat test ktlintCheck build
   coverageReport` gate and review the full diff.

## Regression matrix for the prototype

| Layer | Must demonstrate |
| --- | --- |
| API/IR | `@Input val` accepted; `var`, non-property parameter, unsupported owner/storage rejected; public assignment rejected by Kotlin; getters/property references preserve type and visibility. |
| Calls | Static, one/multiple/dynamic sources; side-effectful initial arguments in source order; named, mixed and default arguments; generic/nullable, nested and inherited constructors; source expression throws; receiver initializer throws; A-to-B compilation; missing-plugin and Java/reflection policy. |
| Graph | Equal results notify zero times; dynamic dependencies unsubscribe; direct/mutual cycles fail deterministically; reentrant invalidations converge; nonconverging effects stop with clear error; listeners can self-disconnect; failing evaluation can recover without duplicate subscriptions. |
| Lifecycle | Initial value before first entry; source changes during construction; receiver exit/reentry/reparent/destroy; source exits or dies first; repeated transitions never double-subscribe; failed construction retains no listener, closure or node. |
| Function | Fish child add/remove/reparent/destruction updates `Text` without replacing it; `connect` skips initial and observes input changes; controlled Slider reports intent only; adapter inputs update without UI scope; static inputs have no upstream subscriptions. |

No tests or benchmarks were run for the original source-only review. The
prototype above has separate verification; the baseline plan itself is not an
implementation claim.
