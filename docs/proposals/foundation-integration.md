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
| 6 | Scoped drawing and efficient terminal rendering (#219) | Pending |
| 7 | UiNode/components and matching compiler integration | Pending |
| 8 | Custom styles/themes and runnable examples | Pending |
| 9 | Launcher, external consumer and headless parity | Pending |

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
