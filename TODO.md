# Canopy maintenance status

Reviewed against merged code and tests on 2026-10-03. This replaces the stale
status in the original external TODO. Pending branches are not counted as merged.

## Completed

- [x] Immutable `Vector2` and non-mutating arithmetic, including `add`, `scl`, and
  `nor`; shared `Zero` is safe because its components cannot change (PR #114).
- [x] Independent node position assignment and repeatable global transform reads,
  with vector and node regressions (PR #114).
- [x] Snapshot iteration in `TreeSystem` and `SceneManager` (PR #116).
- [x] Coalesced `Effect` reruns after an active run; corrected update examples and
  documented single-thread ownership for reactive and manager state (PR #116).
- [x] App lifecycle, pause/frame count, fixed-step cap, input transitions/axes,
  scene teardown/groups/system ordering, registry and terminal bootstrap tests
  (PR #117; lifecycle coverage includes direct loop and headless fixtures).
- [x] Aggregate coverage reporting and a 60% gate (PR #117). Latest local check:
  1,586 / 2,619 executable lines, 60.6%, for enabled modules.
- [x] Remove stale `ANALYSIS.md`, duplicate Mordant module include, and stale README
  version badges; document desktop's existing build blockers (PR #115).
- [x] Clear `SaveManager.loadData` error for missing loaded data, with regression
  coverage (already implemented, covered in PR #117).
- [x] Repository agent contribution rules (PR #119).

## Prepared for review

- [ ] Remove unsupported agent-fix workflows and their generated support files.
- [ ] Route scheduled Dependabot updates through `dependency-updates` (#104),
  provide branch setup/review instructions, and validate both CI target branches.
- [ ] Document public classes and key lifecycle, input, asset, and tooling APIs.
- [ ] Refresh contribution links and companion canopy-docs guidance.

Each item has its own agent-originated branch. Publication follows the user's
separate PR approval stage. Create the permanent dependency integration branch
before merging its routing configuration.

## Deferred engineering work

- [ ] Restore desktop platform imports and adapters, then re-enable and verify it.
  Desktop remains excluded; current aggregate coverage does not include it.
- [ ] Profile global transform reads before deciding whether caching is warranted.
  Any cache must invalidate for local changes, reparenting, and ancestor changes.
- [ ] Evaluate other value types for immutability in separate PRs. Managers,
  nodes, subscriptions, and backend resource wrappers need ownership/lifecycle
  analysis rather than blanket conversion.
- [ ] Plan the next larger lifecycle and architecture tasks separately, including
  pause-aware nodes (#106), queued destruction (#76), and node cleanup (#12).
