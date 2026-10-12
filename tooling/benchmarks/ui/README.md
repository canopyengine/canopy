# UI and terminal composition probes

These synthetic probes separate shared UI CPU/allocation from terminal cell
composition. They exclude physical console I/O, game simulation and application
FPS. Java source lives outside enabled modules and is compiled explicitly.

The original results compare audit commit `e648702` with integration `917af22`, on the same
managed Linux host and JDK 25.0.4.1. Candidate forks ran after builds and launcher
checks finished; preliminary concurrent runs were discarded. Historical baseline
runs occurred earlier in the session, so latency ratios are indicative rather
than a controlled interleaved regression gate. Allocation and operation counts
are the stronger evidence. Raw CSV retains all samples and revision/fork labels.

Three JVM forks each use `-Xms512m -Xmx1g -Xss2m`. Each workload warms up for
500 frames, then records five repetitions of 1,000 shared UI frames or 500 cell
frames. ThreadMXBean measures allocations on the current thread. The shared UI
uses a recording backend and one changing Signal; terminal output goes to a
counting callback. The backend signatures/style constructor were adapted to the
new API; workload sizes, input values and iteration counts remain the same.

| Workload | Baseline median ms/frame | Integration median ms/frame | Baseline bytes/frame | Integration bytes/frame |
|---|---:|---:|---:|---:|
| 1,000 labels, idle | 0.177 | 0.209 | 587,528 | 666,736 |
| 1,000 labels, one changed | 1.010 | 1.268 | 1,680,344 | 2,045,480 |
| 80×24 terminal cells, idle | 0.451 | 0.498 | 2,552,704 | 2,552,704 |
| 80×24 terminal cells, one changed | 0.451 | 0.491 | 2,473,606 | 2,473,606 |

Idle UI performs zero expression evaluations, measurements, paints and backend
submissions. Changing one label evaluates/measures one label but paints all
1,000. Terminal idle emits zero writes; the changed row emits one. The cell
probe deliberately composes a complete frame even when idle; normal UI can skip
submission. The probes are separate and their timings must not be added.

The first integration allocation increase was about 13.5% idle and 21.7%
changed. The following measured slice addresses that regression.

## Reusable snapshot/appearance traversal (`99f4dee`)

The same shared UI probe was rerun in three JVM forks after the full gate,
with no active builds/tests. No cell backend code changed, so the earlier cell
samples remain historical data rather than being represented as new runs.
The workload and probe source were unchanged. Median of 15 samples per case:

| 1,000 labels | Before bytes/frame | After bytes/frame | Allocation reduction | Before ms/frame | After ms/frame |
|---|---:|---:|---:|---:|---:|
| Idle | 666,736 | 256 | 99.96% | 0.209 | 0.067 |
| One changed | 2,045,480 | 734,112 | 64.11% | 1.268 | 1.049 |

The new allocations are also below the original audit baseline: 587,528 idle
and 1,680,344 changed. Idle allocation no longer grows with label count in this
fixture: medians are 440 bytes at 10 labels, 360 at 100 and 256 at 1,000. Small
JIT-dependent differences are not a guarantee of zero allocation. Traversal
still scans the retained state in O(nodes); this is not condition evaluation.

Operation counts remain zero expressions/measurements/paints/submissions when
idle. One change evaluates/measures one label, paints all 1,000 and submits once.
The remaining changed-frame allocation includes measurement keys, layout and
painting. Latencies are indicative on this shared host, not an application FPS
claim or a paired interleaved regression gate. Raw CSV includes all three
revisions, with the candidate tagged `integration-99f4dee`.

The implementation keeps two flat frame-value buffers, swapping only after
successful end; equal/failed frames clear scratch references. Metadata traversal
collects no per-element lists or boxed node identities. Layout values are also
flattened, and appearance walks from the nearest ancestor outward without a
list. Painting keeps stable child snapshots for lifecycle mutation safety.
No new public API, invalidation notification framework or renderer is added.


## Reproduce

Use the repository's configured JDK and export the terminal runtime classpath:

```sh
./gradlew :platforms:terminal:benchmarkClasspath --init-script tooling/benchmarks/ui/classpath.init.gradle.kts
mkdir -p build/ui-benchmark-classes
benchmark_cp=$(cat platforms/terminal/build/benchmark-classpath.txt)
javac -cp "$benchmark_cp" -d build/ui-benchmark-classes tooling/benchmarks/ui/UiAuditBenchmark.java tooling/benchmarks/ui/CellAuditBenchmark.java
java --enable-native-access=ALL-UNNAMED -Xms512m -Xmx1g -Xss2m -Dlogback.configurationFile=tooling/benchmarks/node-cleanup/logback.xml -cp "build/ui-benchmark-classes:$benchmark_cp" UiAuditBenchmark
java --enable-native-access=ALL-UNNAMED -Xms512m -Xmx1g -Xss2m -cp "build/ui-benchmark-classes:$benchmark_cp" CellAuditBenchmark
```

These commands use Bash syntax; Windows uses `gradlew.bat` and a semicolon
classpath separator. Run each probe in three separate JVMs with no builds/tests
active. Baseline source uses the old backend API; this candidate source cannot
be compiled against the baseline unchanged. Historical baseline data is retained
in `results/`, not represented as a new same-time baseline run.
