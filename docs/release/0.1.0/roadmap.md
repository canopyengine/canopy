# Canopy 0.1.0 release roadmap

Baseline refresh: **2026-11-10**. Engine runtime/tests `42515a1`, acceptance
fixture correction `783eee9`, docs `4001f00`, and demos `31f02a0`;
source version **0.1.0-dev4**. See the [release baseline](README.md) for exact
pins, CI links and validation limits. This plan does not authorize merges,
publication, a version bump or changes to maintainer-owned milestone wording.

## Release definition

Deliver a terminal/headless engine with a validated core, small shared UI, reliable native launcher/input/output, matching immutable compiler/runtime distribution, a playable ecosystem reference demo, and a beginner-friendly aquarium tutorial on the documentation site.

The engine-owned frame boundary is approved for 0.1.0: shared preparation,
changed-geometry notification, physics/update dispatch, presentation and failure
abort. Platforms retain native scheduling and implement narrow frame operations.
Validate terminal batching/input acknowledgement, failure recovery and direct
lifecycle drivers. The broader implicit services/capabilities design remains #224.

The runtime contract is one running App per JVM process. Keep startup ownership,
failed-start rollback and successive-run cleanup. Service ownership/access redesign
belongs to 0.2.0 issue #224; concurrent Apps are outside that plan. See the
[ownership audit](app-ownership-audit.md) for retained safeguards and evidence.

Retain the existing milestone's ecosystem requirements: rabbits/foxes, hunger/thirst/energy, grass/water/cover, nine day phases, naturally evolving clear/rain, commands, inspection, pause and reliable cleanup. Implement these in stages rather than replacing the agreed final scope with the initial smaller slice.

The aquarium is the short onboarding project; the ecosystem is the larger reference and integration test. Existing ASCII animation, components and themes suffice. Platform/capability configuration redesign (#224), desktop rendering, shaders, audio, physics, durable saves/replay, advanced widgets and a hosted JVM playground remain future work.

## Current evidence and gaps

- Foundation implementation includes engine-owned frames, one-App ownership,
  self-typed extensible nodes, shared styled UI, grids/dividers, hover, command
  capture and changed-cell terminal output.
- The 2026-10-17 local gate records 605 JVM methods: 604 passing, one Windows
  skip, 90.6% coverage; 11 separate Python checks and 11 staged publications
  validated. These are historical results, not fresh November executions.
- Current recorded Build, Windows filesystem and CodeQL checks pass. Standalone
  Java launcher jobs pass on three hosts, but the external terminal consumer
  fails before launch. Its raw GridNode type is corrected in `783eee9`;
  acceptance remains pending until CI verifies the corrected pin.
- The managed environment failed during this refresh. Fresh local/native checks
  are blocked; the release checklist does not claim all-green acceptance.
- The learning site now has Ink colors/Forest navigation, Nunito typography,
  interactive practice, answer feedback and code-diff highlights. Docs CI passes
  at `4001f00` (64 generated pages). Writing review and deployment remain.
- Aquarium GIFs remain concepts. The Gradle/project skeleton exists with an empty
  Main.kt; the playable game, teaching starter and tutorial are not complete.
- Ecosystem remains a configuration-loading scaffold. Demo CI/build instructions
  now pin engine `783eee9`; the refreshed pin needs consumer verification.
- The review inventory now covers 105 Markdown documents across all three
  repositories: 29 engine, 69 docs and seven demos. All await final freeze/review;
  no human sign-off was generated.
- Phase-1 tracking and the maintainer's milestone wording update are complete.
  Distribution #213 and repaint #219 remain open release gates. Preserve the
  accepted ecosystem scope and keep #224 on 0.2.0.

## Ordered work

### 1. Refresh acceptance and confirm the starting point

The baseline, toolchains, work items and draft PRs are recorded. First verify the corrected external terminal fixture and refreshed demo pin in CI, then run a short live input/color/resize/pause/shutdown pass. Update evidence by tested revision; historical green runs do not satisfy a changed candidate. The live milestone wording was already updated by the maintainer.

Keep #213 in 0.1.0 tracking. Verify #219 against changed-cell/frame batching acceptance; defer more elaborate partial in-memory painting only after demo profiling supports it. Review overlapping terminal PR #220 against the real foundation branch before selecting merge order; avoid duplicating changes or presenting a stale PR as the complete integration.

Use the existing focused work items: docs #55 for the learning site, demos #8 for Aquarium, demos #9 for the ecosystem and engine #225 for release acceptance/human review. Keep these distinct from the existing distribution and repaint issues. Keep #224 on 0.2.0; classify remaining proposals by actual shipped/deferred state. Administrative automation such as #221 does not become a release blocker by default.

Exit: one accurate checklist, known candidate commits and explicit scope. No unrelated dependency or architecture changes enter the release silently.

### 2. Learn the foundation while establishing the small site foundation

Your first study pass follows App/EngineLoop, Nodes/SceneManager, Signals/events/ownership, UiNode/UiManager/layout, InputFocus/commands, terminal backend/cell output, assets/logging, compiler and distribution. Trace a frame, button action and removed node through code and tests. Compiler internals receive a separate deeper pass as needed.

The site's initial design, navigation/search and interactive feedback are implemented. In parallel with study, review that foundation and complete version labels, lesson content and deployment configuration. Use reusable diagrams and animated figures with captions, static fallback, playback controls and reduced-motion behavior. Avoid redesigning the site before testing its first complete lesson.

Exit: you can explain the major ownership/frame flows; the site can present one complete lesson cleanly.

### 3. Build the completed aquarium; extract onboarding from it

Implement three fish, different movement speeds, aiming controls, falling pellets, six-pellet budget, catch/removal rules, counters, accuracy rating, pause and retry. Already-fed fish do not consume more food. Wait for the final active pellet to resolve before declaring an exhausted-budget loss. Keep collision logic based on grid cells and use node lifecycle cleanup.

The current empty skeleton is the starting point. Extract a teaching starter from the working solution that supplies App/scene setup, tank presentation and basic theme. Learners implement Fish/FoodPellet behavior, catches, Signals, controls and game completion. Keep full solution separate. Store starter/completed projects in canopy-demos and chapters in canopy-docs, pinned to matching artifacts/commits.

Write lessons from the working solution, with runnable checkpoints and real output captures. Test the 45–60 minute learner target with someone new to Canopy and comfortable with the stated Kotlin prerequisites. Record installation/download time separately. Make one independent modification part of the exercise.

Exit: playable game, validated instructions, clean restart/exit and measured onboarding attempt. Concept GIFs are replaced or labelled explicitly where actual captures are unavailable.

### 4. Build the ecosystem reference in playable increments

A. Bounded world, agents, grass, population/event UI, seeded wandering, commands, pause, inspection and quit.
B. Hunger, feeding, hunting, death and safe removal; deterministic resolution and iteration.
C. Thirst/water, energy/rest, cover, nine day phases and clear/rain weather. Migrate legacy SUNNY/SNOWY/RAINY/STORMY configuration explicitly.
D. Responsive status/map/events, invalid-input behavior and full accepted workflows. Record real demo workload measurements before adding rendering complexity.

Keep gameplay rules in the demo. Promote a helper into the engine only when a concrete reusable requirement is demonstrated.

Exit: deterministic required workflows pass; the demo remains understandable and matches its outline.

### 5. Freeze behavior and complete documentation and human review

Freeze public APIs, example sources and document structure. Allow bug fixes, tests, clarification, packaging and release metadata. Publish a document-review manifest with file, revision, current/historical status, reviewer, checked examples, comments and sign-off. Group review into getting-started/onboarding, user concepts, maintainer internals, tooling/publication, and remaining reference/historical material. Read every tracked document, including repository and site READMEs.

Use existing Markdown as the single source of truth, with selective interactive components owned by the same docs repository. Provide verified code examples, diagrams/figures where they explain behavior, and interactive visualizations for selected topics such as Signals, lifecycle and input routing. Educational browser visualizations are identified as models; they are not advertised as running the JVM engine.

Check APIs/imports, JDK/toolchain instructions, artifact versions, launch commands, platform support, glossary, links/anchors, screenshots/animations and future-versus-shipped claims. Generated API references cover the enabled public surface. Published defaults and integration-only instructions are distinguished until release.

You review frozen batches, not a continuously changing document set. Changes after sign-off reopen only the affected material and dependents. Offer optional in-site maintainer exercises for quick practice with individual systems. These are learning aids, not exams, prerequisites, required onboarding steps or release sign-off tasks.

Exit: all release-facing writing signed off, historical material clearly identified and examples verified. Completion of learner or maintainer practice exercises is not a release gate.

### 6. Prepare and validate an immutable release candidate

Choose an unused candidate identity, for example 0.1.0-rc.1, and align runtime/compiler/Gradle plugin/plugin marker and example pins. Never overwrite published artifacts. Record the candidate engine/docs/demo source identities.

Run full tests/lint/build/coverage, site/link checks, example compilation and clean consumer installation. Exercise aquarium and ecosystem, multiple successive Apps, focus/hover, command drafts, pause, narrow/wide resize, assets/logging, quit/Ctrl+C and cleanup. Include physical Windows terminal acceptance and supported Linux/macOS environments. Rerun affected checks after fixes and repeat the final candidate gate when candidate code changes.

Validate staging/signatures/bundle metadata before remote upload. Prepare concrete artifacts, checksums, release notes, migration guide, supported-environment matrix and deployment preview for final review. Follow main-only publication workflow prerequisites and repository merge protection; select exact approved merge order across repositories. Dependency updates during stabilization need a concrete reason and relevant validation.

Exit: identifiable candidate, reproduced checks and only explicitly accepted nonblocking limitations.

### 7. Approved merge and public release

After candidate code and writing review, merge the selected PRs in the documented order. Prepare the final unused 0.1.0 identity and run checks on the exact final artifacts/source revision; do not assume RC verification automatically validates a version-modified build.

Obtain release/publication approval for the concrete reviewed result. Publish the matching artifacts, verify download/build/run from Maven Central with an isolated consumer, publish the approved docs site and matching example revisions, and create the source tags/GitHub release at the actual published revision. Account for Central indexing/availability before declaring remote consumer success.

Close #213 only after distribution/remote consumer acceptance. Close #219 after its accepted behavior is merged and verified. Complete the release checklist, reconcile remaining issues, close the milestone only after gates pass, archive evidence and nominate the next development version. Confirm release URLs and examples still work after deployment. GitHub releases/tags, site content and artifact coordinates must agree.

## Optional in-site practice

User and maintainer concept pages can include optional 2–5 minute practice blocks. Keep each block focused on one system, with a small initial state, one manipulation or prediction, observable feedback, a hint, an explanation and reset/show-solution controls. Readers can skip every exercise without losing access to documentation or later chapters. No scoring, timers, certification or mandatory completion tracking.

Maintainer examples: trace frame order; predict when an effect runs; identify which owner disposes an event connection; determine where a removed node leaves a scene index; route an input event with/without command capture; identify changed terminal cells after a text edit; separate the compiler transform from its runtime behavior.

Use the same small site components for user and maintainer practice. Browser diagrams and simulations must identify themselves as educational models. Exercises requiring actual Kotlin/engine execution provide copyable snippets and local instructions until a real hosted runner exists. Test model behavior and explanations against current source/tests; do not imply a browser simulation validates engine execution.

Deliver the first small set with the visual-documentation phase, prioritizing frame flow, ownership, Signals, input routing and cell output. Their correctness and usability are documentation responsibilities; reader participation is always optional.

## Protected human time and planning estimates

Assume Kotlin/Gradle prerequisites. These are engineering planning ranges, not measured completion times or guarantees of unattended progress.

- Assisted implementation/editing/checks: original planning allowance of approximately 85–150 focused hours across site/onboarding, full ecosystem, documentation enrichment, housekeeping and release validation; bugs may add time.
- Your codebase study: reserve 24–40 hours, with more time if compiler IR/toolchain internals are unfamiliar. This establishes practical maintenance understanding, not mastery of every line.
- Your manual writing review: reserve 24–40 hours as an original planning allowance; re-estimate against the now 105-document inventory, new chapters and rework before content freeze. Prioritize public instructions but finish the complete manifest.
- Your live acceptance and final release review: reserve 4–8 hours.

Human study/review/acceptance therefore needs roughly 52–88 hours. Do not add overlapping reading activities twice. At two hours per review day this is 26–44 sessions; at four hours it is 13–22 sessions. Implementation can overlap with study and review of separate frozen batches. Reserve one additional iteration after the newcomer onboarding attempt and live demo testing.

Set a release date only after the aquarium attempt and the first full ecosystem workflow pass, when the remaining defects and review throughput are known. Earlier calendar dates are provisional.

## Completion standard

Release means a new user can install matching published artifacts, finish a guided small game, run the larger demo, understand the documented API and recover from ordinary errors; a maintainer can trace the supported systems and make a small tested change. A passing CI matrix or closed-issue count alone is not release acceptance.
