# Canopy 0.2.0 — provisional release draft

Discussion snapshot: 2026-10-14. Agent-originated housekeeping prepared by Codex.
This collects discussed ideas, not a final milestone, implementation approval,
shipped API or release commitment. Complete and review 0.1.0 first; revisit this
scope using the aquarium/ecosystem feedback. The maintainer owns final milestone
wording and scope selection.

## Intended experience

Make Canopy more expressive and easier to develop with, while keeping one node
model and a small core that a single maintainer can understand. The proposed
headline is a usable desktop backend demonstrated through terminal-inspired
games: graphical windows, readable font sizes, pointer interaction and familiar
shared components. This is a graphical desktop presentation style, not a new
platform between terminal and desktop. Keep the real terminal backend supported.

## Discussed work

| Workstream | User benefit | Planning status |
|---|---|---|
| Implicit host services and capabilities | Shared nodes obtain the active implementation without routine platform blocks | Already tracked in [#224](https://github.com/canopyengine/canopy/issues/224), milestone 0.2.0 |
| Minimal desktop backend | A graphical option with text, basic drawing/grid, existing UI, input, resize and clean shutdown | Proposed headline; audit and prototype required before commitment |
| Basic development mode | Save code, compile automatically and restart after a successful build | Proposed bounded tooling feature; not yet issued |
| Package extension contracts | Add custom DSL blocks and components through ordinary Kotlin dependencies/imports | Proposed foundation; audit existing extension points first |
| Terminal-style theme and recipes | Start a distinctive game without building its whole presentation from scratch | Proposed first-party example package; depends on usable rendering contracts |
| Supported resource reload | Refresh selected assets/configuration without restarting | Candidate only; define ownership and supported resource types first |

## Boundaries that keep the design coherent

- Reuse Node/UiNode lifecycle, ownership and cleanup. Avoid a separate plugin node
  framework or a second service container.
- Ordinary shared nodes express content, behavior and presentation intent. Hosts
  implement their services; themes centralize colors, typography and spacing.
  Truly platform-bound features can use platform-specific nodes/configuration.
- Capabilities can be shared by several hosts. Resolve stable support once per
  run, fail clearly for required missing capabilities, and allow declared optional
  fallbacks. Do not silently drop required behavior or poll capabilities per frame.
- Keep one running App per JVM process, partial-start rollback and isolation
  between successive runs. The approved engine-owned frame boundary is already
  0.1.0 work; do not count it again as a 0.2.0 feature.
- Kotlin extensions can add syntax to public typed scopes. They cannot add an
  unsupported backend capability. Public extension seams need an external-package
  test before promising compatibility with the compiler plugin.

## Small desktop target

Audit the excluded desktop implementation rather than assuming it can simply be
re-enabled. First prove a vertical slice: open a window, render shared text/grid
and a button, receive input, resize, and close cleanly. Use that evidence to choose
rendering dependencies and the actual public API.

If viable, build the smallest supported backend around that slice. Defer the
complete sprite/shader/audio/physics ecosystem. Custom fonts and text sizing are
part of the proposed user value; smooth character movement is an experiment to
validate, not a currently supported API. Actual terminals retain documented
cell-based limits. A theme chooses an aesthetic, not another platform.

## Development mode target

Start with an external supervisor around the launcher: watch source changes,
compile while the current game runs, and replace the game process after a
successful build and orderly shutdown. Only one game instance runs at a time.
Failed compilation keeps the current game running. Runtime crashes retain their
output and log location while the supervisor stays available for another build.

Show whether a change caused a restart or a supported resource reload. Preserve
existing terminal restoration and log ownership. No general live-state
preservation is promised. A future CLI may wrap this tooling; this draft does not
require changing or shipping the Rust CLI.

## Packages and recipes

Use ordinary Kotlin/Gradle dependencies and explicit imports. A package supplies
maintained components or behavior; a recipe supplies readable starting code that
users copy and customize. Prefer composition of existing nodes before adding core
APIs. One first-party terminal-style package can demonstrate a theme and a useful
custom block without making every preset an engine feature.

Possible blocks include `presentation { }` in a host configuration and a
package-provided `diagnosticPanel { }` inside a layout. These are discussion
examples, not selected APIs. A `crt { }` extension additionally requires renderer
effect support; adding a Kotlin block cannot create that capability by itself.

Document supported engine versions, host capabilities, license, installation and
a runnable example. A curated list linking to community repositories can come
later; a dedicated registry and installation CLI are not prerequisites.

## Suggested sequence and work items

1. Collect 0.1.0 release/demo findings and identify real API friction.
2. Audit desktop feasibility and prove the window/shared-scene vertical slice.
   In parallel at the planning level, audit an external Kotlin extension package.
3. Refine #224 using those concrete implementations; choose service access,
   style resolution, capability checks and migration contracts.
4. Implement the bounded desktop backend and demonstrate the same aquarium
   simulation through terminal and desktop presentation.
5. Document extension contracts and supply one reusable theme/component package
   plus editable recipe examples.
6. Add the development supervisor and, only where demonstrated safe, selected
   resource reloads. Verify the full user workflow and update learning material.

Track the prototype, desktop implementation, package extension work and dev mode
as separate focused issues/PRs linked to #224 when scope is selected. Only #224
exists in the milestone today; this draft creates no additional tracker items.
These steps are review units, not a calendar estimate. If desktop proves too
large, reduce its scope or move it out explicitly rather than delaying all work.

## Proposed acceptance

- An external consumer runs the shared aquarium simulation on terminal and
  desktop, with clear presentation differences and capability limits.
- Shared/custom nodes use one lifecycle and service-access model; startup,
  missing-capability and cleanup failures remain diagnosable.
- An external package adds a useful typed block and custom component without
  engine source changes or package-specific compiler rules.
- Dev mode builds, restarts, quits and reports errors reliably on the supported
  operating systems; failed compilation preserves the current running game.
- Migration examples, first-party package examples and optional learning exercises
  match verified APIs. Existing terminal focus, input, reactive and cleanup tests
  remain valid.

## Beyond this draft

Defer unrestricted structural hot swap, automatic state migration, selective
UI/scene reload, dynamic plugin loading, a dedicated community registry, a large
widget/effects suite, CRT shaders, audio/video, physics integration, durable
saves/replay and concurrent Apps. A browser-hosted Kotlin playground is also
separate work. None is implied by a presentation or plugin DSL block.
