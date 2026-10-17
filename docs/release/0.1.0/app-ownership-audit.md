# One-App ownership audit

Audited 2026-10-14 against engine integration `88f7d91` and companion docs
`67242b1`. Agent-originated analysis prepared by Codex; not human sign-off.

## Decision for 0.1.0

Support one running App per JVM process. Keep App-owned manager state,
startup reservation, failed-start rollback and successive-run cleanup.
`launchAsync()` changes the launch thread, not the number of supported Apps.
Lifecycle, registry, scene and node operations remain serialized on the host
thread. Queue producers and shutdown requests have their own cross-thread APIs.
Use a fresh App after stopping the previous instance and completing cleanup.

No runtime code was identified that can safely be deleted solely because
concurrent Apps are unsupported. This change clarifies KDoc, user guidance and
maintainer guidance; it does not change behavior, dependencies or public APIs.

## Inspection and findings

Searched engine, platform, adapter and devtools production sources for scope
selection, concurrent/parallel hosting claims, thread-local contexts, locks and
atomic state. Followed ownership callers, lifecycle drivers and corresponding
tests. Desktop sources were inspected where matched but remain excluded from
the enabled build; this audit does not validate desktop functionality.

| Area | Existing purpose | Decision |
| --- | --- | --- |
| `App.kt`, `ManagersRegistry.kt` | One owned registration map/cache, mode and lifecycle guards; acquire before host startup and release after cleanup | Retain. A rejected App must not dispose the active App's services. |
| `ManagerScope` | Seven-line internal state holder, referenced by two production files | Retain. No thread-local App selection or collection of simultaneously active Apps exists. |
| Standalone `withScope` | Tests/custom lifecycle drivers without an App; replaces and enters standalone registrations | Retain existing API. It is not a nested or concurrent runtime. Review alongside service access in 0.2.0. |
| `EngineLoop` synchronization | Serialize lifecycle calls and reject nested dispatch; retain failures and stop once | Retain. A single App can still have external shutdown callers. |
| App atomics/deferred completion; `HeadlessHost` controls | Async startup, shutdown requests, host publication and lifecycle completion | Retain. Cross-thread control within one App is supported. |
| Input queues, Mordant mouse lock, terminal line-input bridge and surface | Backend producers publish input to lifecycle processing; coordinate prompt/output transitions | Retain. Removing locks would affect current input behavior. |
| Node construction/lifetime, reactive tracking, command bindings | Restore nested calling-thread contexts and roll back construction or owned resources | Retain. Thread-local state does not select an App or make node operations thread-safe. |
| Logback session routing | Directly opened logging sessions share a backend safely and restore host configuration in either close order | Retain. The adapter is usable independently of App hosting. Correct misleading wording about overlapping applications. |
| Managed log-file leases/history | Prevent overwrite/archive conflicts, including separate processes sharing a directory | Retain. One App per process does not imply one process per directory. |

The Logback router does have multi-session state. It is not an alternative App
runtime: direct `LoggingPolicy.start()` calls can overlap without starting Apps.
Removing its ownership accounting would change a separately tested adapter
contract and could restore host appenders while another session still needs them.

## Existing verification contracts

- `AppScopeIsolationTests`: seven cases covering a second App's rejection,
  rejection before host startup, inactive screen shortcuts, repeated launch,
  diagnostic isolation, standalone replacement rejection and ownership through
  shutdown callbacks. Successive fresh runs are exercised in these cases.
- `AppFailureTests` and `AppAsyncFailureTests`: failed-start/runtime cleanup and
  completion/error propagation.
- `ManagersRegistryDispatchTests` and `ManagersRegistryTeardownTests`: dispatch
  mutation restrictions, guard restoration and failure-tolerant teardown.
- `LogbackLoggingTests` and `ManagedLogFilesTests`: independently opened sessions,
  partial-start cleanup, close order, backend restoration and file ownership.

Run these existing tests for this documentation change; no new implementation
or test-only abstraction is required. Record actual results in the integration
PRs. The current release acceptance continues to include native/live terminal
checks; documentation checks cannot substitute for them.

## Validation of this change

- `./gradlew test ktlintCheck build coverageReport --offline --max-workers=2 --console=plain`:
  passes; 600 tests across 84 suites, zero failures/errors/skips, 6,801/7,513
  covered lines (90.5%). The seven test classes listed above contribute 58
  passing tests. Enabled modules only; desktop remains excluded.
- Companion docs `f7f8631`: source documentation checks and the Node 22 site
  build pass; 62 generated pages and their links/assets/anchors validate.
- `git diff --check` and this report's local links/source-test references pass.
- Logs: `app-ownership-gate.log` and `app-ownership-docs.log` under the session's
  `/workspace/audit/codebase-2026-10-12/` evidence directory. No new native
  terminal run is claimed for this documentation-only change.

## Planned 0.2.0 work

Extend [issue #224](https://github.com/canopyengine/canopy/issues/224), rather than
create a competing service framework or another release blocker:

1. Inventory global lookup callers, App-owned registrations, standalone drivers
   and backend services; identify duplicate access and ownership responsibilities.
2. Define one App-owned service access path for shared and custom nodes. Keep
   ordinary nodes free of renderer arguments and platform configuration maps.
3. Decide the compatibility/migration policy for `manager`, delegated lookups,
   `lazyManager`, standalone registry access and test drivers before implementation.
   Cached references must not be advertised as following a later App's services.
4. Consolidate only proven redundant ownership/access machinery. Preserve
   startup reservation, partial-start rollback, shutdown/error ordering, direct
   adapter ownership and isolation between successive runs.
5. Verify a custom node, terminal host and direct lifecycle test driver, including
   missing services/capabilities and failed startup followed by a fresh run.

Concurrent Apps within one process are outside this plan. A later proposal must
first identify a concrete use case and account for backend globals, input,
rendering, logging and lifecycle isolation. No version or API is promised for it.
