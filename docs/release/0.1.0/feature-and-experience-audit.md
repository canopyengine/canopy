# 0.1.0 feature and experience audit

Checked 12 November 2026 against engine integration `f5c1fa8`, docs integration
`7acb112`, demos integration `4618d06`, and the live 0.1.0 milestone/issues.
This change adds the application-startup compiler check described below.

**Conclusion:** the short Aquarium game has its required engine primitives.
The remaining release work is chiefly the two games, accurate learning material,
physical terminal acceptance and an immutable, tested distribution. Another
engine redesign is not justified by this audit.

The maintainer confirmed that Aquarium keeps demos #8's short aiming/feeding game:
three moving fish, six falling food pellets, catches, stars and retry. The recent
five-day water/hunger/coins image is a concept, not the 0.1.0 onboarding specification.
The prepared starter and Kotlin/toolchain prerequisites are outside the learner's
45–60 minute coding target. Aquarium implementation remains the maintainer's work.

## Inventory and scope

At engine `f5c1fa8`, `git ls-files` contains 396 tracked files and 112 JVM test
source files. Counting tracked `.kt`/`.java` under `src/main`, `src/compiler` and
`src/shared` gives 174 production files / 18,555 physical lines, including comments
and blank lines. These are inventory measurements, not complexity or performance scores.

| Area | Files / lines | Release assessment |
| --- | --- | --- |
| engine | 103 / 11,517 | Node/scene ownership, state, events, assets, input, UI and App lifecycle present |
| adapters/libgdx | 4 / 277 | Headless host and existing backend bridge present; not a complete graphical platform |
| adapters/logback | 4 / 957 | Session logs/history/diagnostics and cleanup present |
| adapters/mordant | 1 / 287 | Terminal keyboard/mouse translation present |
| platforms/terminal | 11 / 1,431 | Frame diffing, raw/line input, UI and cleanup present |
| platforms/headless | 1 / 26 | Existing headless App wrapper present |
| tooling/compiler | 18 / 2,383 | Mandatory rules, transformations, provider API and diagnostic formatting present |
| tooling/devtools | 3 / 138 | Existing utilities; no claim of a complete debugger or editor |
| tooling/utils | 2 / 17 | Small shared utilities; no missing release subsystem identified |
| platforms/desktop | 27 / 1,522 | Excluded in settings; unfinished physics/contact paths are outside 0.1.0 |

The inventory also covers root build/settings/catalogs, CI, launcher, publication
scripts, benchmark fixtures, tests and release documents. The standalone launcher
is 354 Java lines; publication bundle/upload helpers are 104/56 Python lines.
Those files are outside the JVM source-set counts above. Inspection checked enabled
entry points, contracts, tests, examples and explicit stubs; passing tests and a
TODO search do not establish that every possible game behaves correctly.

## Code: what is missing

| Priority | Gap and evidence | Next action / owner |
| --- | --- | --- |
| Now | A missing compiler plugin can silently compile a startup call. Engine classes on the classpath are not evidence that the game was processed. | This change: unprocessed `App.launch`/`launchAsync` fail before host startup with `CANOPY_COMPILER_REQUIRED`. Compiler routes processed calls through internal startup entries; missing matching runtime entries emit `CANOPY_APP_ABI`. |
| Release | Aquarium is not implemented in the shared repository: `onboarding/pocket-aquarium/starter/src/main/kotlin/Main.kt` is zero bytes. There is no completed reference project or tutorial checkpoints. | Maintainer builds the short game; extract starter/checkpoints afterward (demos #8). No gameplay is added by this audit. |
| Release | Ecosystem has configuration-backed placeholder nodes. `world/input/CommandHandler.kt` has no dispatch, `simulation/Simulation.kt` only logs readiness, and `logs/EventLogger.kt` has no behavior. | Implement demos #9's agreed simulation, commands, weather/time and event feed in demo slices. Do not move game rules into engine. |
| Release | Current demos/docs checks pin engine `1959501`, before compiler diagnostics #240 and this setup contract. | Move source-validation pins to the accepted new integration revision and rebuild with matching engine/compiler artifacts. Correct the starter README's obsolete reactive-feature branch instruction. |
| Release | Toolchains diverge: integration Kotlin 2.4.10 / serialization 1.11.0; main `82568f2` uses Kotlin 2.4.20 / 1.11.0; dependency-updates uses 2.4.21 / 1.12.0. | Reconcile once before final promotion; rerun compiler/provider and external-consumer gates. Do not mix independently built artifacts or infer compatibility from a shared version string. |
| Release | Mutable local dev4 artifacts are the development path. An exact new signed candidate and fresh remote download/build/run are not accepted yet (#213). | At the end, choose unused matching versions, stage/validate, obtain publication approval and verify isolated consumers. No publication is performed here. |
| Evidence | Changed-cell output is implemented, but full-tree in-memory painting remains. Historical synthetic UI benchmarks exist; they are not measurements of the completed games (#219). | Profile the real Aquarium/ecosystem and record frame time/allocation/output before deciding whether dirty-region painting is necessary. No new performance numbers are claimed. |

### Startup check boundary

This is a setup diagnostic, not a second implementation of compiler rules. It
checks that the startup call was processed. It does not certify every dependency
or scan every Node for a compiler marker. Every Kotlin module declaring custom
Nodes still needs the plugin; existing runtime storage safety remains in place.

Direct calls and typed callable invocations retain defaults, arguments and normal
lifecycle behavior. Java or reflective startup should enter through a Kotlin
wrapper compiled with Canopy. Reflectively invoking the public startup methods
reaches the setup guard. `engineLoop`/manual lifecycle forwarding remains a low-level
embedding interface, not a compiler-configuration check. Objects constructed before
launch can still encounter their existing validation errors first.

Maven Central distributes artifacts; it does not configure a Maven build. The
supported automated setup is the existing Canopy Gradle plugin. A polished Maven
build integration is not implemented or tested; document that limit rather than
advertising equivalent one-line setup. No runtime library can make an absent
compiler plugin reject an otherwise valid Kotlin compilation by itself.

## Developer and user experience: what is missing

| Priority | Gap and evidence | Next action / owner |
| --- | --- | --- |
| Before Aquarium | Instructions can send a user to a superseded feature branch while CI uses a later foundation snapshot. | Keep engine/compiler from foundation integration, correct mechanical branch/pin guidance, and publish them together with the existing root `publishToMavenLocal` task. |
| After Aquarium, before release | A real Node example is incompatible with the new rules: docs `markdown/manuals/guides/runtime-details.md` declares `val displayedEnergy = signal(100)` inside Rabbit. `events-and-signals.md` also teaches `val count = signal(0)` as a subclass field. | During the approved writing pass, replace Node property examples with delegated values and property-reference subscriptions. Compile the chosen user examples, not only the standalone example projects. |
| After Aquarium, before release | The same signal guide teaches handle reads/updates as the main pattern and does not explain observable collection wrappers. Some explicit-owner examples remain valid local/shared handles; blanket replacement would be wrong. | Teach `var hunger by signal(50)`, `val hungry by computed { hunger > 70 }`, and `(fish::hunger).connect { ... }` first. Explain ordinary `=` locals/handles separately. Distinguish collection edits from mutations of ordinary fields inside their elements. |
| After Aquarium, before release | Lifetime timing is easy to misuse: construction, nodeInit, entry, detachment, destruction and listener ownership are different operations. No completed onboarding validates the explanation. | Use Aquarium's actual Fish/Pellet nodes to teach where to create state, connect listeners, update and remove nodes. Explain that guarded ordinary properties do not publish changes. |
| Release | Getting-started instructions intentionally target alpha.1 while source examples/catalogs target dev4. Release baseline/roadmap/tracker verification sections predate the recent repairs; the review CSV lists 105 documents versus 106 currently tracked Markdown files across the three baseline repositories. | After Aquarium, align published/source instructions deliberately, refresh the review manifest including this report, and obtain human writing sign-off. Do not label historical results as current. |
| Release | Automated native Windows input and macOS terminal-window experience are not substitutes for physical acceptance. Line mode intentionally lacks raw pointer/focus behavior. | Maintainer tests raw/line fallback, hover, typing, resize, pause, quit/Ctrl+C and terminal restoration. Record the actual terminal/OS and expected fallback behavior. |
| Release | The site has interactive educational models, not a hosted JVM engine playground. No first-time learner attempt verifies the one-hour path yet. | Complete Aquarium, then write the tutorial and observe a newcomer. Label simulations/GIF concepts accurately; keep exercises optional. |
| Deferred | No live compiler underlines/automatic fixes, automatic dev restart or full graphical font/shader support. | Keep FIR/IDE work, dev mode and graphical kits in 0.2.0 planning. They are not 0.1.0 release blockers. Compiler repository extraction is optional follow-up, not a setup fix. |

For the short game, `GridNode`, Row/Column/Box/Text, Button/Divider, colors, input,
frame callbacks, Signals/computed values, node references and cleanup already exist.
Multi-character fish, pellets, catches, aiming, stars and retry are game code.
A helper in the prepared starter can draw/clear sprites and turn a fish; no general
physics, sprite hierarchy, Timer or progress-bar subsystem is required for this game.

## Recommended order

1. Accept the setup guard and verify both missing-plugin and matching-plugin startup.
   Align companion validation pins with the accepted integration revision.
2. Maintainer develops Aquarium; use real friction/bugs to drive small engine fixes.
3. Complete the ecosystem slices, then measure the games rather than synthetic concepts.
4. After Aquarium, update examples/tutorials and freeze batches for human writing review.
5. Reconcile toolchains and prepare an exact immutable candidate; run local/remote
   consumer gates and physical acceptance against that candidate.
6. Obtain release approval, promote the reviewed integration branches and publish.

No broad engine rewrite, Rust CLI changes, milestone rewrite, release version bump,
remote artifact publication or site deployment is included in this change.

## Verification

Before this change, engine PR #240 passed Build, CodeQL, Windows diagnostic output,
launcher and terminal-consumer CI on Linux/macOS/Windows. Local verification recorded
651 passing tests, one Windows-only skip and 90.2% line coverage. This is baseline
evidence, not the result for the new startup guard.

New local verification passed `test ktlintCheck build coverageReport stageCentralPublication`:
657 tests passed, one existing Windows-only skip, no failures/errors; 7,942 / 8,807
covered lines (90.2%). A fresh external Gradle project compiled successfully without
the plugin but failed at runtime with `CANOPY_COMPILER_REQUIRED` before its host ran.
Adding the existing plugin to that same project produced a successful build/run.
Only isolated local staging was used; nothing was published remotely.

Accepted integration identities and remote CI results are recorded in the PR.
The test cases cover unprocessed calls before host reservation, isolation
from prior processed launches, asynchronous startup, bound/unbound references,
default/vararg arguments, and a single old-runtime compatibility error. Release
acceptance, physical terminal checks and human review remain separate.
