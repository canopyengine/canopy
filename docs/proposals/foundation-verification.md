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
java -Dcanopy.tests.offline=true tooling/launcher/tests/LauncherTests.java .
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
[launcher verification](../../tooling/launcher/tests/README.md#native-terminal-acceptance).
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
`python tooling/launcher/tests/TerminalAcceptance.py --prepare "build/terminal manual" --build`.
Preparation refuses an existing directory. The build installs an external game
and compares its engine/terminal JAR SHA-256 values with the staged artifacts.
The generated command runs the game through the direct Java launcher.

The [six-job workflow](https://github.com/canopyengine/canopy/actions/runs/38360628631)
passes at `5b50eff`: three portable launcher jobs, Linux/macOS native PTY acceptance,
and the Windows external-consumer distribution build with artifact checks.
Windows evidence contains `built: true`, the successful build log and both JAR
hashes. This establishes Windows artifact/compiler/consumer compatibility;
it does not establish native console interaction or restoration.
The [manual Windows checklist](../../tooling/launcher/tests/WindowsTerminalAcceptance.md)
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


## Opt-in terminal UI mouse input (`8e8538e`, 13 October 2026)

This requested feature supersedes the keyboard-only host above. Native input now
requests Mordant `MouseTracking.Normal` and forwards decoded mouse events to the
adapter. Startup still clears inherited reporting modes; normal tracking enables
clicks/wheels without hover/drag motion, and shutdown clears reporting again.
Mouse packets never enter the keyboard text queue.

`UiNode.mouseFilter` defaults to `Ignore`; `Button` opts in with `Stop` and
activates once on a primary press. Custom components override `onMouseInput` and
choose `Pass` or `Stop`. One lease in the existing `InputFocus` service routes
pointers through roots in reverse painter order, below command capture. Hit
bounds respect visibility, tree membership, viewport and ancestor clips. A
consumed UI press stays out of raw gameplay input and mapped actions through
release, even after its widget exits. Command capture also blocks mouse events
in the frame where the editor closes. There is no additional input manager or
public rendering API.

The full local gate passes **575 tests**, zero failures/errors/skips, with
**6,572 / 7,253 covered lines (90.6%)**. The eleven added tests cover default
filters, primary-only actions, Pass/Stop consumption, live painter order,
clipping, ordinary grouping nodes, callbacks that remove their root, terminal
button/wheel decoding, owned gestures and command capture. Exact command:

```sh
./gradlew :engine:ktlintFormat :adapters:mordant:ktlintFormat :platforms:terminal:ktlintFormat test ktlintCheck build coverageReport stageCentralPublication --offline --max-workers=2 --console=plain
```

The fresh external-consumer fixture passes **28 native Linux PTY checks**,
including a real decoded click, wheel handling by a custom component, command
capture without draft edits or underlying actions, existing keyboard controls,
pause/resume, resizing, quit and Ctrl+C restoration. Both launches exit zero
and restore native flags, alternate screen and mouse modes. Exact command:

```sh
python tooling/launcher/tests/TerminalAcceptance.py --offline --java /path/to/jdk-25/bin/java --output /path/to/evidence
```

Companion documentation at `canopy-docs` commit `71b9a85` includes the filter
contract and a custom scroll component. `node scripts/check-docs.mjs` and
`git diff --check` pass. Hover styling, drag-and-drop and GUI pointer capture
remain outside this slice. The updated Windows checklist requires physical
clicks/wheels and prompt capture; Windows consumer compilation and unit tests
alone do not prove interactive console acceptance.

The [full build run](https://github.com/canopyengine/canopy/actions/runs/38393883783)
passes at `8e8538e`, including Linux test/lint/build/coverage and Windows
filesystem/terminal test and lint jobs.

The first [native workflow](https://github.com/canopyengine/canopy/actions/runs/38393897507)
passes Linux PTY acceptance, Windows consumer compilation/JAR identity checks,
and all three launcher jobs. The macOS consumer builds, then its JVM aborts
before the first rendered frame (`exit=134`, `SIGSEGV` in
`SymbolTable::do_lookup` during class loading on the input worker). The retained
`input-hs_err_pid5812.log` establishes a native JVM crash, not a failed pointer
assertion. Its cause has not been established; no production workaround or
retry-on-crash behavior was added. The failed job was rerun unchanged.

The unchanged rerun (attempt 2) passes all **25** main macOS PTY checks,
including native click/wheel decoding, command capture, pause/resume, resizing,
typed quit and full terminal restoration. Its second fresh launch aborts in the
same JVM frame before Ctrl+C acceptance (`exit=134`, `hs_err_pid6325.log`).
Consequently the native workflow remains **failed**, despite all mouse feature
assertions passing. This is a recurring, intermittent startup failure requiring
separate runtime investigation; successful pointer checks do not establish
reliable macOS launch behavior. Linux passes both launches and all 28 checks;
Windows consumer and all three portable launcher jobs pass.


## macOS native memory workaround (`dcae5b9`, 13 October 2026)

Investigation identifies an out-of-bounds native write in Mordant 3.1.0's FFM
macOS terminal interface. Its `termios` layout allocates four 8-byte flags and
32 control bytes: **64 bytes**. Darwin uses 20 control bytes, padding and two
8-byte speeds: **72 bytes**. `tcgetattr` writes the output speed at offset 64,
beyond the FFM allocation. Both retained JVM crashes dereference `0x258c`, with
a damaged pointer of `0x2580` (9600, a terminal speed). This supports native
buffer corruption rather than the earlier suspected general JVM startup issue.
An isolated reflection probe against the actual staged Mordant JAR confirms
`MemoryLayout.byteSize() == 64` without calling the unsafe native function.

Primary sources:

- [Mordant 3.1.0 FFM macOS layout](https://github.com/ajalt/mordant/blob/3.1.0/mordant-jvm-ffm/src/jvmMain/kotlin/com/github/ajalt/mordant/terminal/terminalinterface/ffm/TerminalInterface.ffm.macos.kt)
- [Darwin termios definition](https://github.com/apple-oss-distributions/xnu/blob/main/bsd/sys/termios.h)
- [Mordant 3.1.0 JNA macOS layout](https://github.com/ajalt/mordant/blob/3.1.0/mordant-jvm-jna/src/jvmMain/kotlin/com/github/ajalt/mordant/terminal/terminalinterface/jna/TerminalInterface.jna.macos.kt)

The macOS terminal factory now delegates input/settings to Mordant JNA, which
has the correct native layout. Geometry retains the default backend's safe
query; on JDK 25 this uses FFM ioctl rather than spawning `stty` each frame.
No launcher options, JDK downgrade, parser fork, JVM crash retry or user API
change is required. JNA was already present in Mordant's omnibus runtime graph;
its explicit dependency now permits direct provider selection. Remove this
localized workaround when the upstream FFM layout is corrected.

The routing regression proves raw-mode acquisition/restoration, event reads and
hidden line input use the safe backend, while geometry and automatic resize
queries use the native backend. The full local gate passes **576 tests**, zero
failures/errors/skips, and **6,578 / 7,261 covered lines (90.6%)**:

```sh
./gradlew :platforms:terminal:ktlintFormat test ktlintCheck build coverageReport stageCentralPublication --offline --max-workers=2 --console=plain
python -m unittest discover -s tooling/launcher/tests -p 'test_*.py'
python tooling/launcher/tests/TerminalAcceptance.py --offline --repeat 2 --java /path/to/jdk-25/bin/java --output /path/to/evidence
```

Both Python harness tests pass. The repeated Linux PTY fixture passes **56
checks across four fresh launches**, all exiting zero with terminal restoration.
Companion documentation at `canopy-docs` commit `9200b00` passes content/link
checks. The macOS workflow now runs five complete native iterations (ten fresh
launches); each retains separate evidence. Main, Rust CLI and demo gameplay are
unchanged.

The [full build](https://github.com/canopyengine/canopy/actions/runs/38398024043)
and [six-job native workflow](https://github.com/canopyengine/canopy/actions/runs/38398019313)
pass at `dcae5b9`. The unchanged JDK 25 macOS runner completes **ten fresh
launches / 140 acceptance checks**, including native click/wheel input, command
capture, pause/resume, narrow/wide resizing, typed quit and Ctrl+C restoration.
All ten processes exit zero. Linux native acceptance, Windows staged-consumer
verification, all three portable launcher jobs, and Linux/Windows build tests
also pass. These replace the failed macOS acceptance at `8e8538e`; no Windows
physical pointer acceptance is inferred from its consumer build.


## Owned terminal windows (`e776bfc`, 13 October 2026)

Terminal distributions now open a fresh native window on every launcher run.
Detection reads their installed JARs for `TerminalApp.class`; projects without
that runtime keep their existing console. `--window` forces a new window for
other apps; `--current-terminal` explicitly retains the caller's handles for
PTY/CI/SSH, piped input or headless runs with mixed platform dependencies.
Direct `gradlew run` or a main-class invocation does not acquire this behavior.

Windows uses system PowerShell to create a new console. macOS opens Terminal
and records the particular tab/window it owns, closing only that window.
Linux selects installed kitty, alacritty or xterm with a desktop display.
Arguments stay in UTF-8 data rather than being interpolated into shell code.
The helper has a 64 MiB heap limit; the game's selected JDK and JVM options,
working directory, empty/Unicode/literal arguments and exit status are retained.
Normal exit removes helper files. Color depth follows the emulator's capabilities.

Verification at this change:

- Full local test/lint/build/coverage gate passes: **576 tests**, no failures,
  errors or skips; the existing coverage remains **90.6%**.
- The committed launcher suite passes **seven groups** under Linux Xvfb/xterm,
  including native console attachment, quoted paths/arguments, app exit status,
  launcher cancellation, no surviving game and helper-file cleanup.
- A real staged Canopy consumer opens its own xterm window by default. Its
  rendered screenshot contains exact green `(120,240,120)` and orange
  `(255,140,70)` pixels. Native keyboard input opens commands, typed `quit`
  exits zero, and the window disappears. These are real GUI/TTY checks, not
  a command-construction mock.
- Current-terminal native acceptance still passes **28 PTY checks**, including
  clicks/wheels, prompt capture, pause/resume, resizing and both restored exits.
- JDK 17 compiles/runs the single source launcher; both Python harness tests
  and documentation content/link checks pass.
- The demo's copied launcher is byte-identical to the engine source. Its
  pinned engine revision is updated; build and lint pass against the staged
  repository. Kotlin gameplay sources are unchanged.

Exact local commands (managed environment setup omitted):

```sh
java -Dcanopy.tests.offline=true -Dcanopy.tests.window=true tooling/launcher/tests/LauncherTests.java .
./gradlew test ktlintCheck build coverageReport --offline --max-workers=2 --console=plain
python tooling/launcher/tests/TerminalAcceptance.py --offline --java /path/to/jdk-25/bin/java --output /path/to/evidence
```

The [full build](https://github.com/canopyengine/canopy/actions/runs/38407034153)
and [six-job launcher/consumer workflow](https://github.com/canopyengine/canopy/actions/runs/38407027942)
pass at `e776bfc`. Linux runs graphical window tests in Xvfb; Windows verifies
native console creation, normal app exit and forced game termination. On POSIX,
launcher cancellation uses graceful SIGTERM; Windows forced process termination
cannot run that process's Java shutdown hooks. macOS compiles the owned-window
AppleScript with quoted paths and passes its existing ten-launch native PTY
suite. **Actual macOS GUI opening/closing and its first-use Automation permission
remain live desktop acceptance.** Windows native console creation is automated;
physical pointer/color/resize interaction remains in the live checklist.
Companion docs: `a8e422c`; demo launcher/pin: `96825e8`. Main and Rust CLI remain
unchanged. No release or Central publication is performed by these checks.

## Windows ANSI output follow-up — 13 October 2026

A live Windows 10 starter opened its own console successfully but displayed
literal ANSI color and cursor sequences. Mordant 3.1.0's Windows input backend
does not enable virtual terminal output processing. The terminal surface now
enables processed output and virtual terminal processing on stdout/stderr before
its first screen write, then restores their original modes after screen cleanup.
Redirected handles are skipped. This uses the existing JDK FFM capability,
adds no dependency, and performs no native mode work during frames.

Runtime fix: `b87944f`; final regression fixture: `53223c6`. The native Windows
regression creates a new console with ANSI explicitly disabled, opens the real
terminal surface, checks both mode words and interpreted cursor positioning,
then requires restoration of the disabled modes. Two initial CI runs failed
before the probe started; the corrected fixture uses Gradle's declared runtime
classpath and a Java argument file, with startup/native diagnostics in CI logs.

Verification:

- Full local `test ktlintCheck build coverageReport` passes: 577 tests, zero
  failures/errors/skips, 90.2% aggregate line coverage. The Windows-only native
  assertions run on Windows CI, rather than on this Linux workspace.
- [Windows filesystem/terminal CI](https://github.com/canopyengine/canopy/actions/runs/38419828071)
  passes at `53223c6`, including the real console rendering/mode regression.
- [All six launcher/consumer jobs](https://github.com/canopyengine/canopy/actions/runs/38417897722)
  pass at the runtime fix on Windows, Linux and macOS.
- Demo build/lint and documentation checks pass. The demo distribution's
  terminal JAR hash matches the staged fixed runtime. Docs companion: `0d498c7`;
  demo runtime pin: `0f556ef`. The Java launcher source is unchanged.

Manual pointer/color/resize acceptance still belongs to the Windows live
checklist. To retest an existing starter, rebuild/publish matching engine
artifacts before launching; replacing only its launcher cannot update the
terminal runtime. Main and the Rust CLI are unchanged.

## Bounded grids and compact controls — 13 October 2026

Implementation: `9b860bd`. `GridNode` extends UiNode, measures itself through the
backend and stores sparse cell overrides. `GridSymbol` is an immutable printable
ASCII glyph/color value. Concise set/index/clear/fill operations retain content;
fill uses constant space, and coordinate validation precedes mutation. Layout
keys include spacing while presentation keys include the cell revision, keeping
color/content changes out of layout. Platform drawing is synchronous and clipped.
`Divider` fills its allocated width without focus or default mouse participation.
Terminal buttons now occupy one row, using bracketed labels or borderless padding;
long labels truncate at grapheme boundaries and newlines/tabs become spaces.

Verification:

- `./gradlew test ktlintCheck build coverageReport stageCentralPublication
  --offline --max-workers=2 --console=plain` passes: 586 tests, zero
  failures/errors/skips, 6,696 / 7,405 covered lines (90.4%). Nine new regressions
  cover grid operations/lifetime/cache/resize, bounded rendering and dividers.
  Existing button metrics, Unicode and pointer-capture tests were updated for
  single-row geometry. The two Python harness checks also pass.
- A fresh staged-artifact consumer uses GridNode and Divider directly through
  the matching compiler. All 28 local native PTY checks pass, including moving
  colored cells, mouse/focus/actions, explicit pause, command capture, retained
  narrow/wide drafts and quit/Ctrl+C restoration. An initial fixture placed its
  divider above the tick counter, hiding the counter behind the 32x10 command
  overlay; moving the divider below the counter corrected that fixture layout.
- A real owned xterm under Xvfb displays the dotted grid, colored agents,
  divider and compact focused button. Typed quit exits zero and closes the
  owned window. Screenshots and logs are in the local grid acceptance evidence.
- [Full Linux build and Windows terminal tests](https://github.com/canopyengine/canopy/actions/runs/38456579985)
  and [all six launcher/consumer jobs](https://github.com/canopyengine/canopy/actions/runs/38456583786)
  pass at the implementation commit. macOS repeats five paired native launches.
  Windows native ANSI restoration passes; physical pointer/resize interaction
  remains covered by the separate live checklist rather than the consumer build.
- Docs checks and demo build/lint pass. The demo distribution's terminal JAR
  hash matches the staged runtime. Documentation companion: `54b3dfe`; demo pin:
  `76609a0`. Ecosystem gameplay and the copied launcher are unchanged.

The backend contract adds grid/divider measurement and drawing. Default drawing
reports unsupported components explicitly; custom renderers supporting them must
provide the methods. Desktop rendering and platformConfig are still deferred.
No main merge, version bump, Rust CLI change or Central publication is included.

## UI hover — 13 October 2026

Implementation: `f89204d`. Existing `mouseFilter` values also govern clipped,
front-to-back hover participation. UiNodes expose read-only `isHovered` and
node-owned `mouseEntered`/`mouseExited` events, allocated only when accessed.
Hover remains separate from keyboard focus/actions. Buttons underline by default;
`UiTheme.hoveredButton` customizes it, with focused/disabled styling taking
precedence. Frame reconciliation clears or transfers hover after layout,
visibility, tree or filter changes beneath a stationary pointer.

The terminal reader retains one native raw-input lease capable of motion,
while terminal reporting switches between motion and clicks only. The lifecycle
thread publishes the desired policy to the IO worker through an AtomicBoolean;
the worker never reads node state. Exclusive command capture clears hover and
suppresses motion reporting. Closing the editor requires fresh pointer input.
Pointer packets remain typed mouse events, never keyboard text. Terminal-window
exit has no portable event; the last cell remains known until another input or
layout/capture change.

Verification:

- `./gradlew test ktlintCheck build coverageReport stageCentralPublication
  --offline --max-workers=2 --console=plain` passes: 592 tests, zero
  failures/errors/skips; 6,789 / 7,502 covered lines (90.5%). Six added regressions
  cover transitions, filters, clipping/layers, disabled styling, command capture,
  destruction callbacks and typed/deduplicated motion with held buttons.
- All 31 native PTY acceptance checks pass through the Java launcher, including
  hover without focus/activation, leaving an element, clicks/wheels, prompt
  reporting changes, preserved drafts, pause/resize and quit/Ctrl+C restoration.
  The two deterministic Python harness checks also pass.
- Companion documentation: canopy-docs `abd409f`. Demo pin: `4a24fa4`.
  Demo `test ktlintCheck build` succeeds against the staged repository; its
  packaged terminal JAR hash matches the staged implementation. Gameplay and
  the launcher source are unchanged.
- Cross-platform [build](https://github.com/canopyengine/canopy/actions/runs/38465175765)
  and [launcher/consumer](https://github.com/canopyengine/canopy/actions/runs/38465181147)
  CI were dispatched at the implementation commit and were running when this
  evidence was recorded. Their live results supplement the local checks above.

## Standalone tooling test layout — 13 October 2026

Audited all 66 tracked tooling files. Seven misplaced test/checklist/fixture files
now live under `tooling/launcher/tests/` (five) and `tooling/central/tests/` (two).
The terminal game source belongs in `launcher/tests/fixtures/`; launcher testing
instructions have their own README. Production launcher/publication entry points
keep their original paths. Gradle module tests were already in `src/test/`.
Benchmarks stay in their dedicated directory, and published devtools testing APIs
stay in `src/main/` so user projects can still depend on them.

CI discovery/commands, Python imports, fixture/repository resolution and manual
links use the new paths. `tooling/README.md` documents the distinction; generated
Python caches are ignored. Companion docs: canopy-docs `45d09c3`.

Verification: all 11 standalone Python tests, five Java launcher integration
groups and 31 native terminal checks pass from the new locations. Full
`./gradlew test ktlintCheck build coverageReport --offline --max-workers=2
--console=plain` passes with unchanged engine APIs and runtime. Local tooling and
proposal Markdown links resolve, and no callers retain the former test paths.
The manual `--prepare --build` path also succeeds when invoked from outside the
repository, confirming default repository and relocated fixture resolution.

## Preserved physics pause timing — 14 October 2026

Implementation: `dbfb8d7`. The previous single-clock reset discarded earned time
on every pause/resume transition; six focused cases failed against that behavior.
Running and paused accumulators now preserve their independent remainders and
capped debt. Only the current state's clock advances, keeping paused time out
of resumed gameplay. `Always` nodes receive ticks from both clocks; their cadence
may shift at transitions because both fractional remainders remain independent.

A tick snapshots eligibility before the app physics hook and scene traversal.
Pause/resume inside a callback completes the started tick under its original
eligibility, then ends the burst before another tick. Remaining time belongs to
its original clock. Snapshot restoration uses finally, including callback errors.
Frame processing observes the updated pause state, and step configuration resets
both accumulators. Existing public method signatures are preserved.

Verification:

- `./gradlew :engine:ktlintFormat test ktlintCheck build coverageReport
  stageCentralPublication --offline --max-workers=2 --console=plain` passes:
  600 tests, zero failures/errors/skips; 6,800 / 7,513 covered lines (90.5%).
- Eight new regressions cover rapid toggles through both states, retained/capped
  debt, pause changes in loop/app/node callbacks, clock reset and exception
  cleanup. The existing transition test now checks separate retained remainders.
- All 31 native PTY launcher checks pass with the staged runtime, including
  pause/resume, focused controls, command capture, resize and exit restoration.
- Companion docs `67242b1` update runtime, application and scene-manager timing.
  Source checks and the Node 22 site build/link/anchor checks pass.

The change remains on the integration branch and draft PR #226 for human review.
It is a stabilization fix; no main merge or release publication is included.


## Platform package organization (2026-10-14)

Terminal's `app` package now contains only its application host. Eight internal
implementation files move to `ui`, `graphics`, `input` and `runtime`; eleven test
files follow those responsibilities. Three desktop tests move from obsolete
backend paths to desktop graphics/physics paths. Desktop configuration and the
startup helper now declare their existing platform app namespace. Desktop stays
excluded; these source-only changes do not validate or restore it.

All 66 platform Kotlin source/test paths match their package declarations. The
22 moved Kotlin files retain their declaration bodies after excluding package,
imports and whitespace. The public enabled-platform builders/imports stay the
same. The UI smoke main class and cell benchmark import follow the moves.

Validation:

- `./gradlew :platforms:terminal:ktlintFormat test ktlintCheck build coverageReport
  --offline --max-workers=2 --console=plain`: passes, 600 tests, zero
  failures/errors/skips, 6,801/7,513 covered lines (90.5%).
- `stageCentralPublication` and terminal `benchmarkClasspath` pass; both UI and
  cell Java benchmark sources compile against the reorganized runtime. No new
  performance measurement is claimed.
- 31 native PTY acceptance checks pass using staged artifacts with verified
  engine/terminal JAR hashes, including a second fresh launch.
- Companion docs `83c42be`: source checks and Node 22 site build pass, with
  62 generated pages and validated links/assets/anchors.
- Evidence under `/workspace/audit/codebase-2026-10-12/`: logs prefixed
  `platform-packages-`, with native artifacts in `platform-packages-pty/`.

Package overview: [platform modules](../../platforms/README.md). Pending human
review stays in draft engine #226 and docs #56; main is unchanged.
