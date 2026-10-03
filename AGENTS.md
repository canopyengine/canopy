# Agent contribution rules for Canopy

These rules apply to agent work throughout this repository. Follow the user's
authorized scope and explicit approval stages. Read applicable nested AGENTS.md
files before editing their directories.

## Contribution sources

Read [CONTRIBUTING.md](CONTRIBUTING.md) and the relevant Canopy documentation
before implementing changes. The contribution notes were reviewed in full at
canopy-docs commit `c6f03b2e357106cebbe365d731943f65f54a627c`:

- [Contributing](https://github.com/canopyengine/canopy-docs/blob/main/markdown/contributing/contributing.md)
- [GitHub guidelines](https://github.com/canopyengine/canopy-docs/blob/main/markdown/contributing/github-guidelines.md)
- [Code style](https://github.com/canopyengine/canopy-docs/blob/main/markdown/contributing/code-style-guidelines.md)
- [Testing](https://github.com/canopyengine/canopy-docs/blob/main/markdown/contributing/testing-guidelines.md)
- [Logging](https://github.com/canopyengine/canopy-docs/blob/main/markdown/contributing/logging-guidelines.md)
- [Project guidelines](https://github.com/canopyengine/canopy-docs/blob/main/markdown/contributing/project-guidelines.md)
- [Documentation guidelines](https://github.com/canopyengine/canopy-docs/blob/main/markdown/contributing/documentation-guidelines.md)

The code-style and logging notes are drafts; the documentation-guidelines file
was empty at review time. Apply their published guidance and existing code
patterns. Resolve stale toolchain names, versions, paths, and task examples
against the checked-in Gradle configuration. Surface architectural conflicts
instead of silently changing the architecture to match an old example.

## Scope and implementation

- Keep each branch and PR focused on one problem. Multiple issues may share a PR
  only when they address the same overarching problem. Split unrelated fixes,
  dependency updates, formatting, and documentation cleanup into separate work.
- Read the relevant implementation, callers, tests, and engine documentation
  before changing behavior. Preserve existing contracts unless the requested
  change explicitly alters them.
- Prefer clear Kotlin and existing engine patterns. Keep modules cohesive and
  avoid unnecessary abstractions or public implementation details.
- Respect the separation between engine, adapters, platforms, and tooling.
  Put backend-specific behavior in the appropriate adapter or platform.
- Treat lifecycle order, tree membership, ownership, mutation, and threading as
  explicit contracts. Document changed contracts and cover them with regressions.
- Preserve the immutable Vector2 value contract. Change vector components by
  assigning values. Profile performance concerns before adding caches or pooling;
  a cache must correctly invalidate for local and hierarchy changes.
- Preserve unrelated user changes. Inspect the diff before staging and stage
  only files or hunks belonging to the current task.

## Kotlin style and comments

- Follow `.editorconfig` and the module's ktlint configuration: four-space
  indentation, LF endings, UTF-8, and the configured 120-character line limit.
  Let ktlint enforce imports and trailing commas.
- Add or update KDoc for public engine classes, methods, properties, and signals
  affected by the change. Explain observable behavior, units, ownership,
  lifecycle, and threading where relevant.
- Use `Node.kt` as a reference for class structure, KDoc, and section comments.
  Adapt the structure to the class; small classes do not need empty sections.
- Comment non-obvious decisions and invariants. Avoid comments that merely
  repeat a statement. Keep examples valid for the current API.
- Update the relevant class reference or manual in canopy-docs when scripting
  APIs are added or changed. Identify the companion documentation change in the
  PR; do not claim external documentation was updated when it was not.

## Logging

- Use Canopy's logging APIs and the appropriate subsystem under `io.canopy.engine.*`.
- Choose the correct level and useful structured context. Avoid routine logging
  in hot frame, physics, and per-node loops.
- Do not use `println` for engine diagnostics. Follow existing output mechanisms
  for intentional terminal rendering and build-tool reports.

## Dependencies, modules, and versions

- Adding a dependency or module requires an agreed design; existing user or
  maintainer approval counts. First consider existing modules and dependencies.
- Define dependency versions and aliases in `gradle/libs.versions.toml`, grouped
  by domain. Use camelCase version aliases and `group-module` library aliases;
  reuse a version entry for related libraries where appropriate.
- Add dependencies only to modules that need them. Use `implementation` for
  internal types, `api` for exposed dependency types, and the appropriate runtime,
  compile-only, or test configuration for other uses.
- Use Kotlin Gradle scripts. Keep common build configuration in the root script
  and module-specific configuration in the module. New Kotlin modules must
  enable ktlint and be included once in `settings.gradle.kts`.
- Follow the agreed module placement, kebab-case module names, and short package
  segments. Verify new modules with `./gradlew projects` and the build.
- Follow Canopy's MAJOR.MINOR.PATCH and dev/alpha/beta/rc release conventions.
  Change release versions only as part of authorized release work. Keep version
  documentation consistent with the build configuration.

## Tests and verification

- Add or update meaningful tests for bugs, new behavior, and refactors that can
  affect behavior. Bug regressions should demonstrate the failure being fixed.
- Test observable contracts, including relevant success and failure cases.
  Use descriptive backtick names and Arrange / Act / Assert structure.
- Use shallow `@Nested` groups when they clarify multiple related scenarios.
  Prefer real objects and existing in-memory fixtures over unnecessary mocks.
- Keep tests deterministic and independent. Isolate global registries and clean
  up test state. Avoid network services, real databases, timing sleeps, unseeded
  randomness, filesystem-dependent fixtures, and execution-order assumptions.
- Use the checked-in Gradle wrapper and configured toolchain. On Windows use
  `gradlew.bat` for the corresponding commands.
- Run affected tests and lint checks during development. Before submitting code,
  run the full `./gradlew test`, `./gradlew ktlintCheck`, and `./gradlew build`.
  Combined invocations are acceptable. Run `./gradlew coverageReport` for changes
  to behavior, tests, or coverage configuration; respect the configured gate.
- Do not skip tests, lower coverage gates, or disable modules to make a change
  pass. State any already-disabled modules and resulting verification limits.
- Review `git diff --check` and the complete diff. Record exact verification
  commands and results. If a check is blocked, explain why; mark the PR as a
  draft when authorized to publish an incomplete change. Documentation-only work
  needs relevant content/link checks rather than new unit tests.

## Agent provenance

Every agent-created branch, commit, and PR must disclose its origin. Apply these
conventions to new work; preserve disclosure when squashing or renaming.

| Artifact | Required convention | Example |
| --- | --- | --- |
| Branch | `codex/agent/<type>/<kebab-case-description>` | `codex/agent/bug/fix-system-dispatch` |
| Commit subject | `[Agent] <Imperative description>` | `[Agent] Fix system dispatch mutation` |
| Commit trailers | `Agent-Originated: true` and `Agent: <actual agent/tool>` | `Agent: Codex` |
| PR title | `[Agent] <Imperative description>` | `[Agent] Fix system dispatch mutation` |
| PR label | `agent-originated` | Required in addition to classification labels |
| PR body | An agent-origin statement naming the actual agent/tool | `Agent-originated contribution prepared by Codex.` |

Valid branch types are `bug`, `feat`, `proposal`, `docs`, `refactor`, `test`, and
`chore`, matching the GitHub contribution notes. Start the description in commit
and PR titles with a capital letter. Omit issue-type prefixes such as `Bug:` or
`Feat:` from PR titles. Aim for commit subjects under 72 characters including
the agent marker; separate the explanatory body with a blank line and wrap it
around 80 characters.

Keep the configured Git author identity. Do not invent a human review, signature,
co-author, agent identity, or email address. Do not rewrite published history or
rename another contributor's branch merely to retrofit this convention. Follow
explicit user-specified branch names while retaining provenance in commits and PRs.

## GitHub workflow and labels

- Start each new task branch from the latest `main`. Keep it current before
  publication. For a necessary stacked PR, explicitly identify its dependency,
  base branch, and merge order; keep each diff focused.
- Make each commit a stable, reviewable change and include the relevant tests
  with its implementation. Correct intermediate failures before submission.
- Create or publish PRs only within the user's authorization. Respect any
  requested plan/work/PR approval stages. Merge only when explicitly authorized.
- Fill `.github/pull_request_template.md` with the concrete problem, resulting
  behavior, changes, actual related issues, and verification instructions.
  Describe material side effects and incomplete work.
- Put `Fixes #<number>` in the PR body only when the PR actually resolves that
  issue. Keep closing keywords out of titles and commit messages. Never leave
  the template's `Closes #` placeholder or close an unrelated issue.
- Add `pull-request` and `agent-originated` to every agent-created PR. The actual
  repository label is `pull-request`, despite the older docs' `pull requests` spelling.
- Apply relevant existing classification labels using the table below. Inspect
  the live label inventory before publishing; do not invent module label names.

| Classification | Existing labels to use when applicable |
| --- | --- |
| Bug fix | `bug` |
| Approved new feature | `enhancement` |
| Documentation | `documentation` |
| Maintenance/release/tooling | `operations` |
| CI change | `ci` |
| Dependency change | `dependencies` |
| Experimental work | `experimental` |
| Relevant module | `topic:engine`, `topic:app`, `topic:data`, `topic:input`, `topic:graphics`, `topic:physics`, `topic:logging`, `topic:utils` |
| Relevant platform | `platform:desktop`, `platform:headless`, `platform:terminal` |
| Verification still required | `needs testing` |

`agent-originated` is the general provenance label. Report a missing required
label as a setup gap. The former `agent-fix` automation has been removed; its
legacy label is not a substitute for the general provenance label.

For authorized issue creation, use `<Type>: <Description>` with the documented
types, relevant labels, and the actual project/milestone/branch associations.
Do not invent associations or create extra issues just to satisfy a checklist;
report unavailable metadata. Respond to review with specific, constructive
changes and distinguish required fixes from optional suggestions.
