# Dependency update integration

Scheduled Gradle and GitHub Actions version updates target `dependency-updates`.
Dependabot security updates continue to target the default branch (`main`), as
documented in the [Dependabot options reference](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference#target-branch).

## Setup

Create the permanent `dependency-updates` branch from the latest `main` before
merging the routing configuration. This branch is an integration destination,
so it is preserved after an integration PR merges. Agent task branches still
follow the provenance conventions in `AGENTS.md`.

## Review and integration

1. Keep `dependency-updates` current by merging `main` into it through a reviewed
   synchronization PR. Resolve conflicts without discarding dependency changes.
2. Review each Dependabot PR and its release notes. Merge approved updates into
   `dependency-updates` after Build and CodeQL pass.
3. Open a focused integration PR from `dependency-updates` to `main`. Describe
   the included updates, compatibility changes, and verification results.
4. Merge only after maintainer approval and successful checks. Preserve the
   integration branch, then synchronize it with `main` again.

Build and CodeQL run for pushes and PRs targeting either branch. Build checks
tests, lint, compilation, packaging, and the aggregate coverage gate with JDK 25.
Dependency graph submission runs only for pushes to `main`.

Existing Dependabot PRs may still target `main`; review their bases individually
when enabling this configuration. This change does not enable automatic merging.
