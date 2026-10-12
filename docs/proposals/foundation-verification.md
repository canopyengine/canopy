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
