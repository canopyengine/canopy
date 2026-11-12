# Canopy 0.1.0 release tracking

Baseline refreshed **2026-11-10**. Agent-originated bookkeeping prepared by Codex.
This is an integration checkpoint, not a stable release declaration. The
maintainer owns milestone wording, code/writing sign-off, physical acceptance,
and final merge/publication decisions.

## Baseline

| Repository | Pinned integration revision | Review |
| --- | --- | --- |
| canopy | `783eee9d4d5cecfd89e7931e9f250741a82f2942` | [Engine #226](https://github.com/canopyengine/canopy/pull/226), open draft |
| canopy-docs | `4001f00110a45adaf4784ac953584b28f22f09af` | [Docs #56](https://github.com/canopyengine/canopy-docs/pull/56), open draft |
| canopy-demos | `31f02a0b6a7f784210d83e7bca86be8a0daea49e` | [Demos #10](https://github.com/canopyengine/canopy-demos/pull/10), open draft |

The engine pin includes runtime/test baseline `42515a1` and the terminal
acceptance fixture's `GridNode<*>?` correction. Later changes to this tracking
folder do not change that runtime/fixture snapshot. Demos CI and local build
instructions now pin the same engine revision.

Source runtime, compiler and both demo catalogs remain **0.1.0-dev4**. This
refresh does not bump a version or publish artifacts. Published-installation
instructions still target alpha.1; they are separate from source/local builds.

Toolchains: Kotlin **2.4.10**, Gradle **9.8.0**, JDK **17** for compiler tooling
and **25** for engine/game compilation. Docs use Node **22.12+**, Astro
**7.3.8** and Starlight **0.42.6**.

## Evidence and outstanding validation

The table records historical October results. Fresh November checks are listed
separately below; the full refreshed acceptance gate remains open.

| Check | Recorded result | Evidence |
| --- | --- | --- |
| Engine Build and Windows filesystem | Passed at `42515a1` | [Build run](https://github.com/canopyengine/canopy/actions/runs/39041074582) |
| Engine CodeQL | Passed at `42515a1` | [CodeQL run](https://github.com/canopyengine/canopy/actions/runs/39041074486) |
| Standalone Java launcher | Passed on Linux, Windows and macOS | [Launcher run](https://github.com/canopyengine/canopy/actions/runs/39041077707) |
| External terminal consumer | Failed on all three hosts before fixture correction | Same Launcher run; **new pin requires verification** |
| Docs quality/build/browser checks and published starter | Passed at `4001f00` | [Documentation run](https://github.com/canopyengine/canopy-docs/actions/runs/39041075072) |
| Demo build/CodeQL | Passed at previous pin `d163a8f` | [Build](https://github.com/canopyengine/canopy-demos/actions/runs/38991350247), [CodeQL](https://github.com/canopyengine/canopy-demos/actions/runs/38991350257); fresh results below |

Fresh checks observed during the refresh: engine [CodeQL](https://github.com/canopyengine/canopy/actions/runs/42856723047) passes at `783eee9`. In [Launcher](https://github.com/canopyengine/canopy/actions/runs/42856722770), standalone launcher jobs pass on all three hosts, Linux native terminal acceptance passes, and the Windows external fixture build/artifact verification step passes; macOS consumer acceptance remains pending. [Build](https://github.com/canopyengine/canopy/actions/runs/42856722854) has a passing Windows-filesystem job and pending Gradle job. Refreshed demo [Build](https://github.com/canopyengine/canopy-demos/actions/runs/42856833508) passes at `31f02a0`; [CodeQL](https://github.com/canopyengine/canopy-demos/actions/runs/42856833626) remains pending. Windows native terminal interaction is deliberately skipped in CI and still needs physical acceptance. These are observed job/step results, not a declaration that all workflows have completed.

Last local full gate, recorded 2026-10-17: **605 JVM methods discovered, 604
passed, one Windows-native skip, no failures/errors; 6920/7638 covered lines
(90.6%)**. The separate nine publication and two launcher Python checks passed;
local staging/bundling validated 11 publications. Test consolidation and fixture
reuse preserved the distinct lifecycle, ownership, compiler and native boundaries.

The failing external fixture still declared a raw `GridNode?` after the node
became self-typed. Commit `783eee9` changes it to `GridNode<*>?`. Linux/macOS
logs show consumer compilation failed before a usable frame; Windows
`installDist` also failed. Linux native acceptance now passes with the correction; all-host acceptance
is still incomplete. Keep PR #226 draft and the terminal acceptance gate open.

The managed execution environment failed to initialize during this refresh.
No fresh local Gradle, native terminal or site run is claimed. Verify Actions
on the refreshed integration heads before starting the live baseline pass.
Windows pointer/console and macOS GUI behavior still need physical checks.

## Current feature and experience audit

The [12 November feature/experience audit](feature-and-experience-audit.md) identifies
remaining implementation, setup, learning and release gates against the newer
integration baselines. Earlier verification tables above remain historical snapshots.

## Release workstreams

| Work | Tracking | Current state and next action |
| --- | --- | --- |
| Baseline acceptance | [engine #225](https://github.com/canopyengine/canopy/issues/225) | Verify corrected external consumer and refreshed demo pin, then live terminal smoke |
| Learning site | [docs #55](https://github.com/canopyengine/canopy-docs/issues/55) | Ink/Forest design, navigation and interactive feedback implemented; human review and tutorial writing remain |
| Pocket Aquarium | [demos #8](https://github.com/canopyengine/canopy-demos/issues/8) | Starter folders/Gradle files exist; Main.kt is empty. Build completed game, extract tutorial/checkpoints, observe a newcomer |
| Ecosystem reference | [demos #9](https://github.com/canopyengine/canopy-demos/issues/9) | Configuration scaffold; implement the agreed gameplay slices |
| Distribution | [engine #213](https://github.com/canopyengine/canopy/issues/213) | Local staging works; immutable candidate, signatures, approval and fresh remote consumer remain |
| Changed-cell output | [engine #219](https://github.com/canopyengine/canopy/issues/219) | Implemented on integration; final review, native acceptance and merge remain |
| Implicit host configuration | [engine #224](https://github.com/canopyengine/canopy/issues/224) | 0.2.0; outside this release |

Next sequence: green baseline and live smoke → completed Aquarium → extracted
onboarding → ecosystem → frozen documentation/code review → exact release
candidate acceptance → approved merges and publication.

## Documents and human review

- [Ordered roadmap and planning budgets](roadmap.md).
- [Document-review inventory](document-review.csv): **105 Markdown documents**
  (29 engine, 69 docs, seven demos), refreshed from the pinned repository trees.
  This includes repository/site/supporting documents, not just generated pages.
  Rows are snapshots awaiting final content freeze; refresh edited documents
  again before review. No human sign-off is inferred or filled automatically.
- [Historical verification records](../../proposals/foundation-verification.md).
- [One-App ownership audit](app-ownership-audit.md).
- [Maintainer milestone wording aid](milestone-description-draft.md): historical
  draft; the maintainer already updated the live milestone personally.
- [Provisional 0.2.0 discussion](../0.2.0/README.md).

Human-owned tasks:

- [x] Personally update the milestone description (previously verified).
- [ ] Study the codebase; trace a frame, action, state update and cleanup.
- [ ] Review architecture/code and every document at recorded frozen revisions.
- [ ] Observe a newcomer Aquarium attempt and review game/demo behavior.
- [ ] Exercise physical target-terminal input, colors, resizing, pause and exit.
- [ ] Decide hosting/domain details and review the deployment preview.
- [ ] Approve final merges and artifact/site publication.

Optional exercises remain skippable learning aids, never an exam or review gate.

## Branch reconciliation and scope

At the checked heads, engine/docs/demos were respectively 75/49/9 commits
ahead of main with zero behind; these are dated observations, not live counters.
Older engine #220 still has four commits unique to its branch. Docs #53/#54
each have three unique commits. They remain open drafts: compare patches and
accepted behavior before choosing closure or merge order. Divergence alone
does not prove their work is redundant.

Keep the full agreed ecosystem scope. Desktop/physics remain disabled/deferred.
Broader host capability work belongs to 0.2.0 #224. Preserve revision labels on
historical evidence and published instructions. No main merge, public deployment,
remote Maven publication, milestone-description edit or issue closure is part
of this refresh.
