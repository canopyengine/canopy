# Canopy 0.1.0 release tracking

Agent-originated release bookkeeping prepared by Codex. This folder is the
working checklist, not a stable release declaration. The maintainer owns the
live milestone wording, code/writing sign-off, physical acceptance and final
merge/publication decisions.

## Documents

- [Ordered roadmap and time budgets](roadmap.md)
- [Milestone description draft — maintainer edits GitHub](milestone-description-draft.md)
- [Baseline document-review inventory](document-review.csv): 96 tracked Markdown
  documents at the starting revisions. Refresh it after new material is added
  and before final content freeze. Review completion stays blank until a human
  checks a recorded revision; generated inventory is not sign-off.
- [Implementation and validation evidence](../../proposals/foundation-verification.md)

## Baseline

| Repository | Integration commit | Relation to main at audit | Status |
|---|---|---|---|
| canopy | `f80d2d6` | 53 commits ahead, zero behind; 90 changed files | Build and Launcher CI pass |
| canopy-docs | `45d09c3` | 28 commits ahead, zero behind; 12 changed files | Existing site; writing/site updates still needed |
| canopy-demos | `4a24fa4` | Five commits ahead, zero behind; eight changed files | Configuration scaffold, no playable ecosystem |

Engine source version: `0.1.0-alpha.2`. Published user installation currently
uses `0.1.0-alpha.1`; do not replace those instructions with an unpublished
coordinate. The demo pins runtime `f89204d`; later engine commits are acceptance
records/tooling organization, so this pin is intentionally recorded separately.

Current successful CI evidence:
[Build](https://github.com/canopyengine/canopy/actions/runs/38472969928),
[Launcher/consumers](https://github.com/canopyengine/canopy/actions/runs/38472973011).
Recorded local gate: 592 tests, zero failures/errors/skips, 90.5% line coverage;
five launcher integration groups, 31 native terminal checks and 11 standalone
Python tests. Results belong to their tested commits and do not imply future
changes are already verified.

Release acceptance tracker: [engine #225](https://github.com/canopyengine/canopy/issues/225).

## Release workstreams

| Work | Tracking | Current state | Owner/action |
|---|---|---|---|
| Learning site and optional exercises | [docs #55](https://github.com/canopyengine/canopy-docs/issues/55) | Planned | Codex implementation; maintainer writing/presentation review |
| Aquarium onboarding | [demos #8](https://github.com/canopyengine/canopy-demos/issues/8) | Concept only | Build solution, extract starter/chapters, observe a newcomer |
| Full ecosystem reference | [demos #9](https://github.com/canopyengine/canopy-demos/issues/9) | Scaffold | Implement agreed slices and validate gameplay |
| Distribution | [engine #213](https://github.com/canopyengine/canopy/issues/213) | Local staging/consumer work exists; remote gate remains | Prepare immutable candidate, final approval and fresh remote consumer |
| Cell output | [engine #219](https://github.com/canopyengine/canopy/issues/219) | Integration implements accepted output/batching behavior | Remain open until reviewed, merged and verified; profile optional paint optimization |
| Implicit host configuration | [engine #224](https://github.com/canopyengine/canopy/issues/224) | 0.2.0 | Outside this release |

## Human tasks

- [ ] Personally edit the milestone description from the prepared draft.
- [ ] Study the supported engine systems; trace a frame, an action and cleanup.
- [ ] Review code and all release-facing writing at recorded revisions.
- [ ] Observe the newcomer tutorial attempt and assess game/demo behavior.
- [ ] Run physical Windows and other target-terminal input/color/resize/pause/exit checks.
- [ ] Supply hosting/domain decisions when needed; review the deployment preview.
- [ ] Approve exact final merges and artifact/site publication.

Optional in-site practice can always be skipped. Reader completion is not a gate;
correctness of published practice explanations is part of documentation review.

## Branch and issue reconciliation

The integration branches had no PRs at this baseline. Older engine #220 differs
from the current integration by four commits unique to its branch versus 54
unique integration commits. Docs #54 and #53 each have three unique commits
versus 28 on the integration branch. Divergence does not prove redundancy:
compare intended behavior and patches before selecting merge/closure order.

- Engine #208 and docs #45 are already merged; milestone/proposal references
  saying they are unmerged need correction.
- Engine #147 is a broader UI proposal, partly implemented; keep its future scope.
- Engine #145 includes disabled desktop acceptance; enabled-host visibility does
  not complete that entire proposal.
- Engine #209–#212 are follow-up proposals/measurements. Classify each against
  actual acceptance; do not automatically make all of them 0.1.0 blockers.
- Engine #221 is dependency-workflow administration. Existing manual procedures
  remain usable while that issue is open.
- Docs #3 concerns logging documentation; assess existing logging pages during
  review before closing it.
- Demos #3 concerns the outline and #2 CI; they are not substitutes for the new
  playable-demo issue and should be reconciled against their own requirements.
- Desktop/physics PRs remain deferred while those platforms are disabled.

## Known writing housekeeping

Phase 1 corrects stale site labels and demo-outline dependency-migration claims.
Preserve historical snapshot measurements and published alpha.1 installation
instructions with explicit revision/status labels. Broad writing enrichment and
human sign-off remain the next workstreams.
