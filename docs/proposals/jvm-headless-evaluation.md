# JVM headless host evaluation (#171)

A JVM host can drive the existing EngineLoop and reuse the existing Java asset implementation without LibGDX,
Ktx, Mordant or native dependency artifacts. This is a tested architecture experiment, not a public replacement
for HeadlessApp. Keep the existing headless backend while deciding the production adapter and migration contracts.

## Reproduce

Use the checked-in wrapper and Java 25 toolchain:

```bash
./gradlew :platforms:terminal:jvmHeadlessSmoke :platforms:terminal:jvmHeadlessDependencyReport
./gradlew test ktlintCheck build coverageReport
```

The host, application and standalone smoke are unpublished terminal test sources under
`platforms/terminal/src/test/kotlin/io/canopy/platforms/terminal/experiment`.
`jvmHeadlessPrototypeJar` selects those classes and the existing TerminalAssetsManager/TerminalAssetEntry classes,
including their generated companions. It excludes terminal presentation/input, JUnit and test suite classes.
The JavaExec subprocess uses that selected JAR and the actual resolved engine runtime, not the terminal test classpath.
The selected JAR is not attached to a Maven publication.

The dependency task records component IDs, artifact identities and filenames in
`platforms/terminal/build/jvm-headless/runtime-graphs.tsv`; the smoke checks backend class absence inside its own JVM.

## Observed dependency boundary

Baseline: main `51d982b9b6a0041fd7fe84ee8fd92876c5ce1ac8`.

| Graph | Resolved artifact files |
| --- | ---: |
| Existing headless dependency configuration | 23 |
| Existing terminal dependency configuration | 25 |
| Isolated experiment, including the selected executable JAR | 17 |

Existing platform configurations exclude their own main output. The experiment includes its executable JAR, so these
counts describe the reported artifact sets rather than a complete production-package size comparison.

Compared with headless dependencies, the experiment excludes the LibGDX adapter, gdx, gdx-backend-headless,
gdx-jnigen-loader, gdx-platform natives-desktop (1.14.2), ktx-app and ktx-assets: seven artifacts. Compared with terminal
dependencies, it excludes nine artifacts: the Mordant adapter, colormath, JNA and six Mordant artifacts. Both comparisons
add the selected unpublished experiment JAR. No ad-hoc dependency exclusion is used to make the engine run.

Kotlin, coroutines, serialization, TOML, SLF4J, Logback and its encoder/Jackson stack remain required by the baseline
engine graph. Logging separation is independent work in PR #190. No startup-time, CPU, memory or allocation improvement
is claimed. Production dependency edges and publication metadata remain unchanged.

## Runtime and files

The prototype installs atomic stop controls before entry and runs all lifecycle callbacks serially on its caller.
App.launchAsync supplies the existing non-daemon launch thread; the host creates no additional hidden thread.
A pre-interrupted caller enters and immediately exits with zero frames, preserving the flag and settling teardown.
Interrupted waits restore the flag and exit through finally. Stop controls deactivate before user teardown, and late
retained handle calls cannot interrupt unrelated work on the former caller thread.

Monotonic nanoseconds supply frame deltas and per-frame deadlines; an overrun does not trigger host catch-up bursts.
EngineLoop remains responsible for fixed physics caps, pause remainder resets, frame ordering and resize dispatch.
The prototype validates fps between 1 and 1,000,000,000. It preserves a primary frame/startup error and suppresses later
teardown errors, avoiding self-suppression.

Assets reuse the actual TerminalAssetsManager/TerminalAssetEntry implementation. Internal and External use the working
directory, Local uses user.home, Absolute uses the supplied path, and Classpath reads from resources. Classpath entries
are read-only, directory enumeration is unsupported, and missing classpath resources never fall back to the filesystem.
The standalone smoke reads resources from its selected JAR and exercises all five sources and write failures.
These Java mappings are not a promise of identical LibGDX source semantics on every platform.

## Compatibility gaps and decision

The existing LibGDX headless launch is asynchronous and uses its default update rate; the prototype's launch blocks
until teardown and honors AppConfig.fps. Production adoption must decide those contracts explicitly. The global manager
registry still limits independent concurrent applications; this experiment does not isolate it.

Baseline App completes its stopped handle during exit before a later host frame failure can be reported. The prototype
preserves the thrown frame failure, but cannot make join report it without changing core lifecycle completion. Resolve
that contract under [issue #193](https://github.com/canopyengine/canopy/issues/193). PR #179 shares cleanup bookkeeping
and does not fix host failure reporting. The prototype does not claim a production-ready host.

Recommended next design: a shared JVM asset adapter with a compatibility path for existing terminal asset APIs, then an
opt-in JVM headless application with agreed launch/interrupt/failure semantics. Keep rendering adapters independent.
Do not remove the existing backend until those decisions and platform-specific asset differences are reviewed.

## Verification

The full test, ktlintCheck, build and coverageReport gate plus smoke/report tasks passed: 296 tests in 45 enabled
suites, zero failures/errors/skips, and 81.0% coverage (3280/4049). All 12 prototype regressions passed. Independent
review approved the source, build wiring and documentation. All 19 normalized publication metadata files and 21
existing compile/runtime/test graphs are unchanged; the experiment configuration and JAR are unpublished.

Deterministic fake-clock/sleeper tests cover lifecycle
threading, physics/pause, deadlines, interrupted waits, stop controls, late handles and failure aggregation. A separate
finite-frame real-JVM smoke verifies absent backend classes, filesystem/JAR assets and external sleep interruption.
Desktop remains excluded by the baseline settings; no native desktop runtime was exercised.
