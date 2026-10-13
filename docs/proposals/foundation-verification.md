# Foundation distribution verification

Agent-originated evidence prepared by Codex on 12 October 2026. Engine:
`917af22`, `codex/agent/refactor/foundation-integration`, version
`0.1.0-alpha.2`. Tests ran on managed Linux with JDK 25.0.4.1 and Gradle 9.8.0.
Main and the Rust CLI were unchanged. These checks do not publish a release.

## Results

| Check | Result | Limits |
|---|---|---|
| Java launcher integration suite | All five groups pass | Linux only in this session |
| Maven staging | Enabled engine/platform/tooling artifacts and compiler plugin staged | Local files, not Central publication |
| Separate consumer | Compiles and launches using staged Maven coordinates | Warm dependency cache; offline resolution |
| Real headless host | Two successive Apps complete, custom styled UiNode updates, one shutdown callback each | No terminal rendering backend |
| Real terminal through launcher | PTY renders starter, crosses 80×24 → 100×30 resize, Escape opens prompt, typed quit exits zero | Automated stream evidence; no human visual acceptance |
| Rendering comparison | Initial regression recorded; reusable-buffer follow-up reduces allocations | Synthetic probe, full changed-tree painting remains |

The launcher's integration groups cover invalid options; classpath/submodule
application with custom distribution, JVM options, literal arguments, stdin and
exit status; JPMS; root application; build failure and temporary-file cleanup.
The default environment Java 21 was not visible to Gradle toolchain detection;
using configured JDK 25 resolved that environment failure.

Exact commands from the engine checkout (environment-specific paths omitted):

```sh
java -Dcanopy.tests.offline=true tooling/launcher/LauncherTests.java .
./gradlew stageCentralPublication --offline --max-workers=2 --console=plain
java tooling/launcher/CanopyLaunch.java -p /path/to/consumer --gradle-arg --offline --gradle-arg --max-workers=2
java tooling/launcher/CanopyLaunch.java -p /path/to/consumer --gradle-arg --offline -- --terminal
```

The consumer is a separate Gradle build, with the public Kotlin/Canopy plugin IDs,
JDK 25, and `engine`, `platforms-terminal`, `platforms-headless` coordinates at
`0.1.0-alpha.2`. Its plugin/dependency repositories include the staged
`build/central-repository`; there is no `includeBuild`, project dependency or
manual compiler JAR injection. It uses copied wrapper files, as any launcher
project requires. The terminal source is the starter's command/pause/resume/quit
example. The headless source declares a custom component through `UiRoot`:

```kotlin
class CounterCard : UiNode<CounterCard>("Card") {
    private val count = signal(0)
    override fun nodeInit() {
        ui {
            Text("Count: ${count()}").also {
                it.style = UiStyle(appearance = UiAppearance(
                    foreground = UiColor.Rgb(80, 220, 120)
                ))
            }
        }
    }
    override fun onUpdate(delta: Float) { count.update { it + 1 } }
}
// In each App's onEnter:
manager<SceneManager>().currScene = UiRoot { CounterCard() }
```

Each headless run requests exit after its third application update and waits
for its handle. Both observed five updates, five component updates and one exit
callback. Headless exit is asynchronous; exact stopping-frame counts are not a
contract. The first fixture incorrectly assumed three and was corrected.

The PTY has a controlling terminal, starts at 80×24, resizes to 100×30 after
rendering, sends Escape and then `quit` plus Enter. Exit status is zero and the
logging session ends normally. This proves terminal input survives the Gradle
build/launcher handoff; it does not prove every resize/focus/pause detail or
Windows/macOS native behavior.

The preceding complete production gate passed 556 tests with no failures,
errors or skips and 6,350 / 7,095 covered lines (89.5%). This verification adds
benchmark sources/evidence, not production behavior. Desktop remains disabled.
See [benchmark data](../../tooling/benchmarks/ui/README.md) for the initial
allocation increase and follow-up below, and [integration plan](foundation-integration.md)
for live acceptance and ecosystem work remaining before main.

## Rendering follow-up (`99f4dee`)

The full gate now passes 560 tests with no failures/errors/skips and 6,385 /
7,129 covered lines (89.6%). New contract checks exercise repeated changed-frame
commit failure/retry, direct visibility/text/resize changes under ordinary
nodes, root layering/order/removal/reentry, and nearest style/theme overrides.
Existing focus, compiler, abort and terminal partial-output tests also pass.

The unchanged shared UI probe ran three forks after the gate. At 1,000 labels,
median idle allocation falls from 666,736 to 256 bytes/frame, and one changed
label from 2,045,480 to 734,112. Idle backend submissions remain zero; one change
still evaluates/measures one label and paints all 1,000. Median CPU time is
0.067 ms idle and 1.049 ms changed, compared with the prior integration's
0.209 and 1.268 ms. Allocation/operation counts are stronger evidence than
shared-host latency ratios; these results do not measure game FPS.

See the benchmark README/CSV for all samples, baseline comparison and remaining
costs. The previous distribution/PTY results above belong to `917af22`; this
follow-up changes internal shared traversal and comparisons, with no public API
change. Live platform acceptance and ecosystem workflows remain required.

## Native terminal follow-up

The fresh-consumer PTY fixture now exercises 21 input/rendering checks and three
checks on a second Ctrl+C launch. Linux passes all 24: moving green/orange markers,
keyboard focus, button actions, conditional clearing, command capture without
pausing, explicit pause, controls while paused, closing without implicit resume,
draft-preserving 80×24 → 32×10 → 100×30 resizing, resume, quit and Ctrl+C.
Both launches return zero, log a normal session end, restore the alternate screen
and restore the native input/output/control/local flag words. Installed engine
and terminal JAR SHA-256 values match the staged Maven artifacts.

This test reproduced a real shutdown bug: the game entered with local flags
`0x8a3b` and returned with raw flags `0x0a30`. The old coroutine flow blocked
indefinitely in native input; cancellation alone did not close it before JVM exit.
Two regressions (requested exit and interrupted launch) fail before `88c0092`
and pass after it. Native reads now use 100 ms deadlines on IO, and host shutdown
awaits their raw-mode cleanup before teardown. Interrupted status is preserved.
Line fallback retains its existing blocking System.in path; it has already left
raw mode and is not joined by this cleanup. This timeout concerns native input
cancellation; UI conditions remain subscription-driven.

The final local test/lint/build/coverage gate passes 562 tests, no failures/errors/
skips, with 6,434 / 7,136 covered lines (90.2%). Local Maven staging also passes.
The standalone fixture/source and exact reproduction steps are in
[launcher verification](../../tooling/launcher/README.md#native-terminal-acceptance).
Its emulator dependency is test-only and adds no game/engine dependency.

The [three-platform launcher run](https://github.com/canopyengine/canopy/actions/runs/38313249086)
passes all groups on Linux, Windows and macOS. The expanded
[native workflow](https://github.com/canopyengine/canopy/actions/runs/38317590561)
passes all five jobs: launcher checks on Linux/Windows/macOS and native PTY
acceptance on Linux/macOS. The macOS PTY job also passes its targeted repeat.
The earlier run `38316173156` exited before the first macOS frame; that early
exit has not reproduced in either subsequent run. Exit-status/crash diagnostics
are now retained rather than assuming a transient failure is fixed. Track any
recurrence with the stored evidence. Windows native console acceptance remains
manual because Python's Unix PTY interface does not exercise ConPTY. These
launcher checks are not Windows native rendering acceptance.


The existing ecosystem scaffold was tested in a temporary copy, changing only
its Canopy version to `0.1.0-alpha.2` and adding the staging repository to plugin/
dependency resolution. Its Kotlin sources compile unchanged, configuration loads
at runtime and Ctrl+C exits zero with native flag/screen restoration. The actual
`canopy-demos` checkout remains unchanged. This proves scaffold compatibility;
it does not implement the still-missing rabbit/fox simulation, calendar, weather,
resource decisions or narration.

## Windows fixture preparation (`5b50eff`)

The fixture can now be prepared persistently on any OS without pyte:
`python tooling/launcher/TerminalAcceptance.py --prepare "build/terminal manual" --build`.
Preparation refuses an existing directory. The build installs an external game
and compares its engine/terminal JAR SHA-256 values with the staged artifacts.
The generated command runs the game through the direct Java launcher.

The [six-job workflow](https://github.com/canopyengine/canopy/actions/runs/38360628631)
passes at `5b50eff`: three portable launcher jobs, Linux/macOS native PTY acceptance,
and the Windows external-consumer distribution build with artifact checks.
Windows evidence contains `built: true`, the successful build log and both JAR
hashes. This establishes Windows artifact/compiler/consumer compatibility;
it does not establish native console interaction or restoration.
The [manual Windows checklist](../../tooling/launcher/WindowsTerminalAcceptance.md)
covers visual behavior and compares console input/output modes before and after
typed quit and a separate Ctrl+C run. No engine/game/Python dependency was added.

Local verification repeats the complete test/lint/build/coverage gate: 562 tests,
no failures/errors/skips, 6,434 / 7,136 lines covered (90.2%). The updated Unix
harness passes all 24 checks. Dependency-free preparation also passes under
`python -S`, and repeat preparation leaves the existing source unchanged.
Companion documentation content and repository-local links pass their checker.

## Ecosystem development scaffold

The demos side branch `codex/agent/chore/foundation-demo-integration` now selects
`0.1.0-alpha.2`, pins engine `5b50eff` for CI and includes the same bare Java
launcher. `-PcanopyRepository=<URI>` selects matching staged artifacts in both
plugin and dependency resolution, or the existing Maven Local path can be used.
No Kotlin gameplay sources changed. The Unix wrapper is executable in the clone.

Against the engine's staging repository, `./gradlew ktlintCheck test build
--offline --max-workers=2 --console=plain
-PcanopyRepository=file:///workspace/canopy/build/central-repository` passes.
The scaffold has no test sources; its `test` task is `NO-SOURCE`, not a simulation
test pass. A controlling Linux PTY runs the copied launcher from the real demo
project, observes configuration load, sends Ctrl+C and verifies exit zero,
alternate-screen restoration and all four native terminal flag words. The copied
launcher is byte-identical to the engine launcher. This prepares development;
it does not add the missing simulation, commands, calendar or narrated UI.

At demo commit `ac592e7`, the
[demo build workflow](https://github.com/canopyengine/canopy-demos/actions/runs/38366791244)
also passes: it publishes pinned engine `5b50eff` to Maven Local, then builds and
checks formatting in the demo. Dependency submission is intentionally skipped
for this manually dispatched side-branch run. No main branch was changed.

The live 0.1.0 terminal milestone still has 45 closed issues and one open issue,
[#219](https://github.com/canopyengine/canopy/issues/219), at this check. The work
serves its lifecycle/UI/distribution/demo release gates; it adds no desktop,
physics, audio, durable-save or standalone CLI release commitment. Whole-tree
in-memory painting remains documented; terminal output is diffed and batched.
Windows native acceptance, full ecosystem workflows and independent onboarding
remain the outstanding acceptance work before main/release.

## Windows logging path assertions (`767ee22`)

Live Windows testing exposed five assertion failures where JUnit's temporary
base used an 8.3 short user-profile name but `ManagedLogFiles` returned the
canonical long name. The logger deliberately resolves user-selected ancestors
once; it does not follow managed child symlinks. The production behavior is
unchanged. Expected paths now canonicalize only the base before appending the
managed child name, preserving exact child-path and symlink-safety assertions.

A new ancestor-alias case checks current and overlapping isolated runs against
the canonical base on any supported filesystem. All 28 logback tests pass
locally, and the complete test/lint/build/coverage gate passes 563 tests with no
failures/errors/skips and 6,434 / 7,136 covered lines (90.2%). The Build workflow
now includes native Windows logging tests and lint with both configured JDKs.
The Windows logging job and full Linux build gate both pass in
[workflow run 38377876069](https://github.com/canopyengine/canopy/actions/runs/38377876069).

## Classpath JAR handle cleanup (`1acbaa1`)

Windows live testing found JUnit unable to delete `resources.jar` after the
classpath resource test. Closing the entry's input stream did not close Java's
cached JAR handle. A new regression reads an archive, deletes/recreates it at
the same path, then reads the replacement and deletes it again. Before the fix
Linux returns the old content (`original` instead of `replacement`); Windows
also exposes the retained file lock. Each terminal classpath read now disables
caching on its own URL connection before opening its stream. Global cache
settings and other class loaders' handles are unchanged.

Terminal tests/lint and the full local test/lint/build/coverage/staging gate pass:
564 tests, no failures/errors/skips, 6,437 / 7,139 covered lines (90.2%). The Windows
filesystem CI job now runs both logback and terminal module tests/lint. Companion
asset documentation explains the resource lifetime and passes content/link checks.
The Windows filesystem and full Linux build jobs pass in
[workflow run 38379590711](https://github.com/canopyengine/canopy/actions/runs/38379590711),
including the original file/JAR classloader test and the archive-replacement
regression. This verifies archive release without skipping JUnit cleanup.

## Keyboard-only terminal input (`47420f3`)

Windows live testing exposed legacy mouse-report bytes in the command draft.
The terminal host requested Mordant `MouseTracking.Normal` even though it
forwarded only keyboard events. Native input now requests `MouseTracking.Off`.
Session start/end also disable standard mouse-reporting modes, including modes
left enabled by an earlier application. The command editor and ordinary text
input are unchanged; no text-pattern filtering or new input subsystem was added.

Three native-input/session contract checks fail before the fix and pass after
it. The full local test/lint/build/coverage/staging gate passes 564 tests, no
failures/errors/skips, with 6,436 / 7,139 covered lines (90.2%). The external
consumer PTY fixture passes 26 checks: its emulator starts with mouse-reporting
modes enabled and verifies startup clears them and command capture keeps them
disabled. Existing animation, keyboard focus/actions, pause/resume, resize,
draft retention and quit/Ctrl+C restoration also pass. The Windows manual
checklist now explicitly checks movement, clicks and dragging with the editor
open; physical Windows pointer interaction remains a live check.

The [full Linux/Windows build run](https://github.com/canopyengine/canopy/actions/runs/38383583936)
passes. The first native run `38383588168` passes macOS PTY acceptance, Windows
consumer compilation and all three launcher jobs. Its Linux job passes both new
mouse-mode checks and all gameplay checks, but its Ctrl+C wait falsely fails with
`game exit=0`: the child exits between the predicate's status poll and a second
status poll. This is a test-harness race, not an observed game shutdown failure.
At `ff9ec40`, process status is captured before evaluating the success predicate.
The deterministic race test fails before and passes after; a separate negative
test confirms unmet output expectations still fail. Both helper tests and a
repeat of all 26 local PTY checks pass. Production code is unchanged in this
follow-up; the helper tests now run on all three consumer CI platforms.
The final [six-job native workflow](https://github.com/canopyengine/canopy/actions/runs/38384891319)
passes at `ff9ec40`: Linux/macOS PTY acceptance, the Windows external-consumer
build, all three portable launcher jobs and both harness tests on each consumer
platform. No Windows physical mouse-motion acceptance is claimed by these jobs.
