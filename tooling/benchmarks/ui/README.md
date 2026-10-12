# UI and terminal composition probes

These synthetic probes separate shared UI CPU/allocation from terminal cell
composition. They exclude physical console I/O, game simulation and application
FPS. Java source lives outside enabled modules and is compiled explicitly.

Results compare audit commit `e648702` with integration `917af22`, on the same
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

The allocation increase is real in this workload: about 13.5% idle and 21.7%
changed. Full-tree snapshot/paint work remains a follow-up; these results do not
establish a rendering performance improvement.

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
