# Reactive graph probe

This standalone probe compares receiver-owned `Computed` values with receiver-owned
constructor input runtime values on the same engine build. It measures 1,000 and
10,000 values with idle reads and one-source fan-out updates; static inputs are
measured only for idle reads. Each case warms up for 100 iterations and reports
five repetitions. It seeds constructor storage through the compiler ABI before
timing, then measures the current runtime path, not compiler lowering or
rendering. It is not a historical before/after result or a JMH benchmark.

Run on JDK 25 after building the engine (PowerShell):

```powershell
.\gradlew.bat :engine:benchmarkClasspath --init-script tooling/benchmarks/node-cleanup/classpath.init.gradle.kts
$benchmarkClasspath = (Get-Content -Raw engine/build/benchmark-classpath.txt).Trim()
New-Item -ItemType Directory -Force build/reactive-benchmark-classes | Out-Null
& "$env:JAVA_HOME/bin/javac.exe" -cp $benchmarkClasspath -d build/reactive-benchmark-classes tooling/benchmarks/reactive/ReactiveGraphProbe.java
& "$env:JAVA_HOME/bin/java.exe" -Xms1g -Xmx1g -cp "build/reactive-benchmark-classes;$benchmarkClasspath" ReactiveGraphProbe
```

Run the same commands and JVM options on the same host for each candidate build.
Compare medians, allocation and expression evaluations per iteration, not a single repetition. The
computed case is the existing reactive foundation comparator. One fan-out update
invalidates every derived value. The probe asserts zero idle reevaluations and
exactly one reevaluation per derived value for each fan-out update.
The computed case calls `Computed.invoke()` directly, while input cases use the
guarded Node storage lookup in `InputRuntime.read()`. Those access paths differ,
so their timings are not a like-for-like regression gate. A compiler-generated
computed-property benchmark is still needed before making a release claim.
The probe does not establish a performance claim for conditional dependencies,
hierarchy queries, deep graphs, or concurrent access.

## Illustrative run

On Windows with Corretto 25.0.4.1 and `-Xms1g -Xmx1g` on 2026-11-18, the
10,000-value cases produced these five-repetition medians (one process, cases
run in the listed probe order):

| Kind | Workload | Milliseconds/iteration | Bytes/iteration | Evaluations/iteration |
| --- | --- | ---: | ---: | ---: |
| Computed | Idle | 0.274 | 0 | 0 |
| Reactive input | Idle | 0.397 | 0 | 0 |
| Static input | Idle | 0.555 | 0 | 0 |
| Computed | Fan-out | 2.632 | 10,556,968 | 10,000 |
| Reactive input | Fan-out | 5.334 | 10,556,968 | 10,000 |

This run confirms no idle reevaluation or allocation in these cases, but input
reads include a guarded storage lookup that direct computed reads omit. The
timings vary substantially between repetitions and are a profiling lead, not
evidence of a release regression or an acceptable performance threshold. Keep
the raw CSV from each candidate run
when comparing revisions; `build/reactive-benchmark.csv` is only a local output.
