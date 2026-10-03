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
2. The weekly Dependency batches pass selects Dependabot PRs for automatic
   merging into `dependency-updates`, subject to current-revision CI.
3. After five merged dependency PRs, or a staged critical security fix, the
   automation opens one integration PR from `dependency-updates` to `main`.
4. Only humans may approve and merge that integration PR, after successful checks.
   Preserve the integration branch; the resulting `main` push synchronizes it
   automatically.

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
when enabling this configuration.

## Weekly review and critical security fixes

The weekly pass runs Monday at 08:00 UTC (08:00 Lisbon in winter, 09:00 in summer).
Manual dispatch also selects a batch. GitHub scheduled runs may be delayed.
Selected PRs receive `dependency-auto-merge`; that label keeps them queued while
fresh checks finish. Hourly and Build/CodeQL completion passes continue queued
work and scan for critical fixes. These continuation passes do not select new
ordinary version updates between weekly runs.

Only Dependabot PRs targeting `dependency-updates` and verified critical-fix
staging PRs are merge candidates. The current head must contain the current
dependency branch tip. Stale branches are updated and CI is explicitly dispatched.
Both Gradle build and CodeQL must succeed on the current revision. Pending, failed,
cancelled, neutral, or skipped checks block merging, except the known main-only
Dependency submission job, which is deliberately skipped on dependency CI.
All latest commit statuses must also succeed. Each merge uses the expected head
SHA; branch rules remain enforced by GitHub. Configure required checks with strict
up-to-date validation on `dependency-updates` to close concurrent-base races.

Dependabot security PRs continue to target `main`. The automation reads public
GHSA advisories referenced by their descriptions and treats only GitHub's
`critical` severity as urgent. Missing severity/advisory evidence does not imply
criticality. It creates a separate snapshot PR into `dependency-updates`, checks
that snapshot, and merges it only after the same gates pass. The original security
PR stays available for human review. This stages critical security updates only;
non-critical security PRs on `main` remain human-managed.

The workflow submits no approval reviews. If dependency-branch policy requires
reviews, a human must provide them before the automated merge can complete.
For unattended dependency merging, leave approval reviews optional on this branch
while requiring successful, up-to-date Build and CodeQL checks. Keep human review
requirements on `main`.

Enable "Allow GitHub Actions to create and approve pull requests" in Settings >
Actions > General > Workflow permissions so the automation can create staging and
integration PRs; it does not use the approval capability. The default token
permissions can remain read-only because the workflow explicitly requests its
permissions. Allow squash merging. The built-in token is used; a merge queue may
instead require a separately configured GitHub App.

The integration threshold counts dependency PRs not included in the previous
merged integration snapshot, including dependency PRs merged manually. Grouped
Dependabot updates count as one PR. The counter survives workflow restarts and
human squash merges. New work updates an existing integration PR instead of
opening duplicates; a human-authored description is preserved. Closing an
integration without merging does not clear its pending count.

Integration PRs disclose GitHub Actions origin and carry the repository's
provenance and dependency labels. **The automation never approves, merges, or
enables auto-merge for a PR targeting `main`.** Use required human/code-owner
reviews on `main` to enforce that policy for other actors too. Build and CodeQL
are explicitly dispatched on the integration branch; PR-specific runs created by
the built-in token may require a maintainer to approve workflow execution.
