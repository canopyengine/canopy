# Canopy maintenance and release status

Reviewed against the combined integration implementation on 2026-10-08.
[Engine PR #208](https://github.com/canopyengine/canopy/pull/208) and
[docs PR #45](https://github.com/canopyengine/canopy-docs/pull/45) contain the
consolidated housekeeping work, now merged into `main`. Maven namespace migration
#214 is merged too. Source prepares `0.1.0-alpha.1`; it is not a published Central
release or stable 0.1.0. See [publication preparation](tooling/central/README.md).

## Implemented in merged main

- Direct-type TreeSystem membership; ordered scene membership and lifecycle cleanup.
- Shared cleanup aggregation, reactive subscription bookkeeping, atomic input batches,
  key normalization, asset/save identity safeguards and immutable transform values.
- Node-owned lifetime helpers, guarded automatic property storage, compiler validation
  and synchronous failed-construction rollback, including explicit Java fallback.
- Host failure propagation through AppHandle and failure-resistant teardown.
- Minimum backend-neutral declarative layouts, text, buttons, focus, expression capture
  and retained/keyed structural updates, with a terminal backend.
- Node visibility, command open/close activation, bottom overlay input capture and
  adaptive terminal viewport/layout behavior. Opening commands does not pause by default.
- Separate Logback adapter and documented measurement/evaluation fixtures.

Historical benchmark and audit files retain their original revisions and measurements;
they are not current release verification reports.

## Remaining 0.1.0 release gates

- [x] Merge consolidated engine PR #208 and docs PR #45.
- [ ] Finish the deterministic command-driven ecosystem demo: rabbits/foxes, nine day
  phases, food/water/cover, naturally evolving weather, minimum status/events UI,
  live commands and explicit pause/resume.
- [ ] Exercise the full demo including resize, focus, lifecycle and shutdown.
- [ ] Establish immutable matching engine/compiler/plugin publication and verify a fresh
  external consumer. Signed Central staging is prepared; owner credentials, Portal validation/publication
  and remote consumer verification remain pending.
- [ ] Finalize release notes, supported limits and extension examples.
- [ ] Decide whether a simple CLI ships with 0.1.0; no supported `canopy new` exists.
- [ ] Declare the feature freeze after demo validation; accept release fixes and packaging.

## Deferred engineering work

- Restore desktop source/adapters before enabling and validating that platform.
- Broader declarative UI roadmap beyond the minimum terminal release scope (#147).
- Platform-independent physics; fixed physics callbacks alone are not simulation (#137).
- Production JVM-only headless adoption remains a design decision (#171 evaluation).
- Multi-app registry isolation, atomic durable save persistence and deeper traversal
  work require explicit contracts and evidence; they are not established release promises.
- Profile transform or layout hotspots before adding caches, pooling or abstractions.

The [documentation roadmap](https://github.com/canopyengine/canopy-docs/blob/main/markdown/misc/roadmap.md)
and GitHub milestone track release work; open issues whose fixes are in PR #208 stay
open until their implementation merges.
