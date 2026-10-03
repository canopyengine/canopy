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

- [x] Remove unsupported agent-fix workflows and generated support files (PR #120).
- [x] Route Dependabot updates through `dependency-updates` (PR #121).
- [x] Document public classes and lifecycle, input, asset and tooling APIs (PR #122).
- [x] Refresh maintenance status and contribution guidance (PR #123 and companion docs).
- [x] Merge each main update into the dependency staging branch (PR #128).
- [x] Weekly checked dependency batches, integration PRs after five merged updates
  or a critical-security update, and human review for integration to main (PR #131).

## Documentation refresh

- [ ] Publish and review the prepared 0.1.0-dev2 manual, site and README updates.
- [ ] Publish and review demo build/quality workflows and current API migration.
- [x] Align docs/demos main rules with engine reviews, squash-only merging,
  force-push/deletion restrictions, and repository-specific required checks.

Prepared documentation and demo branches follow the user's separate PR approval
stage. The new docs/demo check names require their prepared workflows to land.

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
