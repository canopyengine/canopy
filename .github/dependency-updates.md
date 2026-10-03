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

1. Every push to `main` automatically merges the latest `main` into
   `dependency-updates` through the Sync dependency branch workflow. Resolve
   reported conflicts manually without discarding dependency changes, then rerun
   the workflow from its Actions page on `main`.
2. Review each Dependabot PR and its release notes. Merge approved updates into
   `dependency-updates` after Build and CodeQL pass.
3. Open a focused integration PR from `dependency-updates` to `main`. Describe
   the included updates, compatibility changes, and verification results.
4. Merge only after maintainer approval and successful checks. Preserve the
   integration branch; the resulting `main` push synchronizes it automatically.

Build and CodeQL run for pushes and PRs targeting either branch. Build checks
tests, lint, compilation, packaging, and the aggregate coverage gate with JDK 25.
Dependency graph submission runs only for pushes to `main`.

The synchronization uses the built-in `GITHUB_TOKEN`, with repository contents
and Actions write permissions. It uses normal merges and pushes, retries concurrent
branch updates, and stops on conflicts or branch-protection rejection. Required PR
or restricted-push rules on `dependency-updates` must permit this automation for
direct synchronization to work. It cannot keep the branch current through a merge
conflict without a manual resolution.

Token-created pushes do not trigger ordinary push workflows. The sync workflow
explicitly dispatches Build and CodeQL on `dependency-updates`, including on a
manual rerun, so validation does not depend on a personal access token.

Existing Dependabot PRs may still target `main`; review their bases individually
when enabling this configuration. Dependency update PRs still require approval;
only synchronization from `main` is automatic.
