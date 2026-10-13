# Terminal 0.1.0 foundation integration

Agent-originated implementation plan prepared by Codex, 12 October 2026.

The engine integration branch is `codex/agent/refactor/foundation-integration`.
It starts with terminal presentation at `e648702` and current main at `36d3285`.
Companion documentation uses `codex/agent/docs/foundation-integration`.
Neither branch is a release declaration. Main receives the combined result only
after the complete branch and required ecosystem workflows have been tested.

## Contracts

- Support one running App at a time. Reject a second startup without altering
  the running app; successive apps do not inherit configuration or resources.
- Keep one ownership tree: UiNode extends Node, and UI components can contain
  other UI components or ordinary nodes. Embedding does not make a
  platform-specific drawable portable.
- App coordinates the frame, SceneManager handles membership/simulation,
  UiManager handles reactive composition/layout/focus, and the host presents.
- Reactive conditions subscribe to their dependencies; do not poll expressions
  every frame. Ordinary animated content may need drawing every frame.
- Constructor arguments initialize a retained component once. Signals provide
  live values, as with ordinary Nodes. Keyed reordering preserves initialization
  and state; omission destroys the component through the normal node lifecycle.
- A failed update or paint does not present pending content. Cleanup preserves
  the original error. Display preparation is not application-state rollback.
- Preserve node identity, guarded state, safe mutations, input consumption,
  borrowed-resource leases, logging isolation and failure reporting.
- Keep public engine/compiler artifacts matched. No Rust CLI changes.

## Review units

| Unit | Work | Status |
|---|---|---|
| 1 | App-local screen bootstrap | Implemented; isolation regressions pass |
| 2 | TOML public dependency exposure | Implemented; consumer regression passes |
| 3 | Failed-frame discard and retry | Implemented; UI/terminal regressions pass |
| 4 | App-owned runtime/services and explicit stages | Implemented; full verification passes |
| 5 | Lifetime contract and node-local reactive factories | Implemented; existing guarded storage retained |
| 6 | Scoped drawing and efficient terminal rendering (#219) | Implemented text drawing slice; full verification and Linux/macOS PTY pass |
| 7 | UiNode/components and matching compiler integration | Implemented; full verification passes |
| 8 | Custom styles/themes and runnable examples | Implemented; full verification and Linux/macOS PTY pass |
| 9 | Launcher, external consumer and headless parity | Three-platform launcher and Linux/macOS PTY pass; Windows console acceptance pending |

Keep independently reviewable commits. Component/compiler changes ship together.
Node-storage replacement and headless-host replacement require evidence and are
not automatic consequences of this plan. Desktop, physics/audio, durable saves,
advanced UI features and the standalone CLI remain outside the terminal release.

The first correctness batch passes `test ktlintCheck build coverageReport`:
521 tests, no failures/errors/skips, and 5,872 / 6,580 covered lines (89.2%).
Screen leakage, TOML consumer compilation and failed-frame regressions were
observed failing before their corresponding fixes. These results include current
main's logging changes and are not the earlier audit's 491-test baseline.

After app-owned manager state and single-App startup/shutdown guards, the full
gate passes with 529 tests and 5,933 / 6,640 covered lines (89.4%). Ordinary
manager lookup remains a lifecycle-thread facade; standalone scope replacement
and direct teardown cannot take over a running App. Its reservation remains
held through logging/user shutdown callbacks, and inactive App screen shortcuts
cannot navigate the running App.

Node-local event/signal/computed/effect factories now bind their explicit node
receiver, including constructor initializers. Global factories retain ambient or
explicit ownership. UI observers reuse the core dependency-subscription helper.
The lifetime gate passes with 531 tests and 5,932 / 6,639 covered lines (89.4%).
An optional storage replacement is deferred: no measured evidence yet justifies
changing retained-facade, detached-resource or reusable scene-manager contracts.

Layout measurement now keys constrained width separately from allocated height.
Content/Fixed heights reuse metrics across height-only allocation changes;
Fill/Fraction heights and measured child sizes remain part of layout dependencies.
Regressions reproduced ten labels being measured twenty times and sibling labels
being measured again on a height-only resize. They now verify ten initial calls,
one call for one changed label, no height-only calls and correct width wrapping.
The full gate passes with 533 tests and 5,942 / 6,649 covered lines (89.4%).
These counts describe the fixture, not an application FPS claim. Changed frames
still paint the tree into memory before terminal cell diffing. This does not
complete the UiNode/compiler migration.

The scoped drawing slice adds Node.onDraw(DrawContext), ordinary subtree drawing
and retained ordinary children inside UI scopes. App's existing post-update UI
boundary coordinates drawing; SceneManager tracks entered drawing-node membership,
and the existing backend buffers/presents. No extra rendering manager or public
viewport renderer is introduced. Drawing uses container-local logical units and
automatic clipping, not Node2D transforms. Text is the initial shared primitive;
custom colors/backgrounds/borders remain in unit 8. Contexts expire after callbacks.
Drawing remains live each frame while entered, including while paused; hidden or
detached subtrees do not draw. UI-only equal frames still skip submission. Reactive
conditions remain subscription-driven. Managed construction now defers child entry
while a parent initializes, preserving normal parent-first lifecycle ordering.

The current UiScope.node(site) factory retains exactly one fresh ordinary child;
unit 7 will supply direct declarations in the new component/compiler API. Reorder
retains constructor state; omission queues normal destruction. Regressions cover
clipping/overflow, order, pause, clearing, invalid-factory rollback, failure retry,
context expiry and real compiler-generated conditions with node-owned Signals.
The full test/lint/build/coverage gate passes with 540 tests, no failures/errors/
skips and 6,073 / 6,768 covered lines (89.7%). Desktop remains disabled. These
automated checks do not replace live terminal testing or prove an FPS improvement.

UiNode now extends Node with the same self type, guarded state and lifecycle.
UiRoot also extends UiNode; Row, Column, Box, Text and Button are individual
classes in engine.ui.components. Shared layout/composition lives on UiNode;
text/action state lives on the appropriate leaf class. UiKind is internal policy.
Custom components override nodeInit and use one ui builder, then declare children
through ordinary constructors. The compiler retains both UiNode and ordinary Node
constructors by source site/key. Arguments initialize once without subscribing
the surrounding structural condition to their reads; Signals provide live values.
Existing DSL spellings now return concrete component types. UiScope remains the
construction/binding receiver rather than a second public component model.

All retained children use one Node entry, including transparent structural regions.
Ordinary grouping nodes are layout-transparent to their first UI descendants.
Custom UiNode drawing uses its own content area before children; lifecycle resumes
bindings through ordinary grouping nodes. Permanent destruction skips invalidated
facades during scope traversal. The latter cleanup regression failed before its
guard was added. Compiler fixtures cover constructor-only conditions, keyed custom
components, constructor state, ordinary drawing/grouping, anonymous objects,
reentry and destruction. Typed styles/visibility/eligibility retain expression
capture. Consumers replace UiElement with UiNode<*> or the concrete leaf type and
recompile against matching engine/compiler artifacts. Colors/themes remain unit 8.

The final component gate passes test, ktlintCheck, build and coverageReport:
546 tests, no failures/errors/skips, 6,162 / 6,891 covered lines (89.4%). Companion
docs explain the API, UiScope's purpose and frame/ownership flow. Independent
onboarding and live terminal acceptance have not been established by these checks.

Styling now uses immutable UiAppearance, UiColor RGB/default values and UiTheme
defaults on UiNode subtrees. Null inherits; explicit Default colors and false
emphasis reset inherited values. Component appearances override defaults and
ancestor appearances; focused/disabled button appearances apply last. Borders
may be absent, rounded, square or double and reserve one logical unit per edge.
Button backend measurement includes its chrome without double-counting borders.
UiStyle separates its cached geometry key from appearance, so color/emphasis
changes repaint without text measurement. The compiler also captures theme
assignments. Drawing contexts inherit appearance and accept per-agent overrides.

The terminal backend converts styles through Mordant and paints into the existing
buffer. Backgrounds include control padding; border/background work is clipped
before allocation, even with huge off-screen bounds. Per-glyph focus storage is
removed because the resolved appearance already supplies it. The terminal smoke
now includes a styled, bordered map with moving colored markers; it remains an
API example rather than the required full ecosystem simulation.

The styling gate passes test, ktlintCheck, build and coverageReport: 556 tests,
no failures/errors/skips, 6,350 / 7,095 covered lines (89.5%). Regressions cover
paint-only changes, explicit inheritance resets, theme capture, layout insets,
per-agent appearance, border variants, button backgrounds and bounded off-screen
painting. Bordered clipping containers also protect their content area from
oversized UI descendants, with the same clip used for pointer targeting.
A color-only label update produces one terminal write, leaves the stable
row untouched, avoids screen clearing and submits nothing on the next equal frame.
No application FPS claim is inferred from these checks. Desktop remains disabled.

## Verification and independent onboarding

Run the existing test/lint/build/coverage gates and meaningful regressions for
each changed contract. Retain zero unchanged terminal writes, stale-content
clearing, resize correctness and wide-character behavior. Compare UI allocation
and dispatch budgets with the audit baseline before claiming improvements.

The companion documentation must support a Kotlin developer completing these
tasks without asking the original maintainer for hidden implementation details:

1. Build the engine/compiler and run an external terminal consumer.
2. Trace startup, one frame and shutdown through the owning classes.
3. Implement and style a reusable component, then embed an ordinary map node.
4. Explain initialization, entry/removal/destruction, and source/subscription
   lifetimes; diagnose an invalid retained reference.
5. Trace a signal write through binding, layout, drawing and presentation.
6. Add a regression, run the required checks and locate a failed-frame cause.
7. Locate the compiler transforms and their fixtures, with Kotlin compiler
   prerequisites stated explicitly.

Provide a repository map, exact lifecycle ordering, ownership table, compiler
binding contract, runnable examples, diagnostic guide and migration notes.
Keep this guide aligned with the implementation; mark pending behavior clearly.
Learning-time estimates are planning targets, not measured onboarding results.

The full branch is ready for main only after these checks, live terminal
resize/input/pause/shutdown testing, and the rabbits/foxes demo's required
0.1.0 workflows. Audit completion or a passing synthetic probe is insufficient.

## Final distribution verification and remaining work

At engine commit `917af22`, Maven staging, a separate real-artifact consumer,
two successive headless apps and the Java launcher's five integration groups pass.
A Linux controlling-PTY smoke resizes 80×24 to 100×30, opens the command prompt
with Escape, types `quit` and exits with status zero. Windows/macOS native
terminal controls and visual resize/pause/focus acceptance remain unverified.
See [verification evidence](foundation-verification.md) for exact scope and commands.

The first allocation comparison exposed a regression. The follow-up at
`99f4dee` streams metadata into reusable frame buffers, flattens layout keys and
child collection, and resolves appearance without an ancestor list. Frame state
is committed only after successful end; stable child snapshots remain during
painting. Direct state, visibility, theme, focus, order/removal and retry
contracts stay covered without a new public API or dirty-notification system.

The full gate passes 560 tests, no failures/errors/skips, with 6,385 / 7,129
covered lines (89.6%). The unchanged three-fork shared UI probe at 1,000 labels
now allocates a median 256 bytes/frame idle (previously 666,736) and 734,112 when
one label changes (previously 2,045,480). These are below the original audit
baseline too. Idle frames still evaluate/measure/paint/submit nothing; a changed
label evaluates and measures once but paints all 1,000. State comparison remains
O(nodes), and changed-frame layout/paint costs remain visible. See the committed
[raw data and reproducer](../../tooling/benchmarks/ui/README.md).

Before main: complete native terminal acceptance and the required ecosystem
workflows. Profile actual demo workloads before adding a more elaborate partial
painting mechanism. Independent onboarding still needs a developer to complete
the documented exercises. No Rust CLI, main merge, Central publication or release
declaration is part of this verification.


Native Linux acceptance now passes all 24 checks through a fresh Maven consumer
and the launcher, including visual cell/style assertions, pause/focus/actions,
draft-preserving narrow/wide resize, quit and Ctrl+C. This exposed and fixed an
idle-input shutdown leak: native input now closes before teardown, with bounded
IO reads and preserved interrupted status. Both regression cases fail before
`88c0092` and pass after it. The final local gate is 562 tests, zero failures/
errors/skips and 90.2% line coverage. Launcher CI passes on Linux/Windows/macOS.
Native macOS PTY acceptance now passes twice in CI; Linux PTY and all three
launcher jobs pass too. The first early macOS exit is recorded with improved
diagnostics for any recurrence. Windows console acceptance, the complete
ecosystem workflows and independent onboarding remain separate acceptance.
The old demo scaffold compiles unchanged against the new artifacts in a temporary
version/repository migration and loads configuration at runtime; its actual
checkout has not been modified.

Windows acceptance now has a persistent fixture preparation command and an exact
manual checklist with native console-mode comparison. The terminal consumer CI
matrix includes a Windows distribution build and staged-JAR identity check;
this is separate from native interactive acceptance. See
[Windows test steps](../../tooling/launcher/WindowsTerminalAcceptance.md).
