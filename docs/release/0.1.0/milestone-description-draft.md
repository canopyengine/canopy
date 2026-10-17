# Milestone description draft — maintainer action required

The maintainer owns the live milestone wording. This draft has not been applied.
Edit the description at https://github.com/canopyengine/canopy/milestone/1, using its Edit action.
Keep or change the existing title as you prefer. Suggested description:

---

First stable Canopy release: a terminal-ready engine with a headless host, validated core systems, minimum shared declarative UI, a portable Java launcher, matching immutable engine/compiler artifacts and runnable documentation.

Published development artifacts currently include 0.1.0-alpha.1. The current foundation integration uses 0.1.0-alpha.2 from source/local staging; it is not a stable 0.1.0 release. No release date is declared.

Release gates:
- Validated node/scene/system lifecycle, guarded state/ownership, Signals/events/effects, cleanup and failure handling.
- Terminal keyboard/mouse/hover, focus and command capture, adaptive layout, styled controls/grids/dividers, changed-cell output, pause and native terminal restoration.
- Portable launcher and matching compiler/runtime/plugin distribution, fresh consumers, migration guidance and reliable example projects.
- Pocket Aquarium onboarding: a small feeding/aiming game, prepared starter, completed solution and guided tutorial evaluated with a newcomer.
- Deterministic ecosystem reference: rabbits/foxes, grass/water/cover, hunger/thirst/energy, nine day phases, naturally evolving clear/rain, commands/inspection, responsive UI and clean shutdown.
- Documentation site with checked code examples, diagrams, captioned figures/animations and selected optional user/maintainer practice. Exercises are voluntary learning aids, not exams or prerequisites.
- Maintainer codebase study, human review of release writing and physical target-terminal acceptance. Public release follows explicit merge/publication approval and successful installation from published artifacts.

The original consolidated engine PR #208 and docs PR #45 are merged. Later foundation work remains on the integration branches; older terminal/documentation PRs overlap and require reconciliation. The aquarium is not implemented and the ecosystem is a configuration-loading scaffold. Passing CI is evidence, not a release declaration.

Freeze new features after required application workflows are validated. Stabilization accepts necessary bug fixes, tests, documentation and packaging. Profile real demo workloads before adding rendering complexity.

Deferred: implicit platform/capability configuration (#224, 0.2.0), graphical desktop release, shaders, physics/audio, advanced widgets, durable saves/replay, hosted JVM playground and optional Rust CLI. None is a 0.1.0 promise without an explicit scope change.

Release checklist and roadmap: https://github.com/canopyengine/canopy/tree/codex/agent/refactor/foundation-integration/docs/release/0.1.0

Agent-originated draft prepared by Codex for the maintainer's own editing.
