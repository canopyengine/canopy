# Node cleanup validation and benchmarks

This file preserves the original benchmark and verification snapshot. The compiler
checks and construction cleanup are now included in integration PR #208; the
stacked-PR status and test counts below remain historical evidence.

Measured locally on 2026-10-04 using Windows, AMD Ryzen 7 5800HS, JDK 25,
`-Xms1g -Xmx1g`, and logging disabled. Baseline: latest `main`,
`17fa44cae8af3b8ce5b3832d0eed6971ea914941`. Candidate: the unpublished cleanup
worktree on `codex/agent/refactor/queue-node-destruction-cleanup` after merging
main. The candidate source is included in the accompanying local review diff.

Identical balanced eight-child scenes use EmptyNode2D and a FramePost system
reading each node's guarded name and managed position. Each size has 150 warmup
ticks followed by 200 measured ticks (100 at 50,000 nodes), repeated five times
in one JVM per revision. Candidate and baseline run sequentially with no build
or test process active. ThreadMXBean measures allocations on the dispatch thread.
No sleeps or forced GC are used. Construction exercises cached runtime definition
validation; property reads exercise the managed-storage guard.

## Results

| Nodes | Main median tick (ms) | Candidate median tick (ms) | Faster | Main bytes/tick | Candidate bytes/tick |
| --- | --- | --- | --- | --- | --- |
| 1,000 | 0.342758 | 0.155515 | 54.6% | 943,968 | 47,512 |
| 10,000 | 3.417626 | 2.662469 | 22.1% | 9,412,968 | 443,128 |
| 50,000 | 28.322327 | 24.838242 | 12.3% | 47,052,968 | 2,203,128 |

All three sizes meet the at-most-5% median tick regression gate. Total measured
steady-state allocations are lower at every size. Private traversal uses cached
snapshots and preloaded state; guarded property access creates diagnostics only
on failure. These measurements cover this workload, not every game or backend.
Raw CSV columns are nodes, repetition, tick milliseconds, allocated bytes per
tick, construction milliseconds, cleanup milliseconds, cleanup kind.

Candidate median complete queued cleanup takes 6.238, 17.247 and 100.178 ms at
1,000, 10,000 and 50,000 nodes respectively. Main only supports legacy scene
detachment here; it cannot provide the same permanent destruction guarantee.
Its cleanup timing is therefore not an equivalent destruction comparison.

The separate deterministic cleanup check retains all facades, queues the root,
advances a frame, and asserts exactly N index removals for N nodes, zero retained
private states, zero index entries, zero system matches and every facade invalid.
`results/cleanup-operation-counts.csv` records nodes, index removals, retained
states and index entries. Counts are exactly 1,000, 10,000 and 50,000 removals.
The engine regression suite additionally covers overlapping and reentrant queues,
paused flushing, failure-resistant teardown, ownership and reusable detachment.

## Reproduce (PowerShell)

In the candidate checkout, export its classpath:

```powershell
.\gradlew.bat :engine:benchmarkClasspath --init-script tooling/benchmarks/node-cleanup/classpath.init.gradle.kts
$benchmarkClasspath = (Get-Content -Raw engine/build/benchmark-classpath.txt).Trim()
$benchmarkLog = (Get-Item tooling/benchmarks/node-cleanup/logback.xml).FullName
& "$env:JAVA_HOME/bin/javac.exe" -cp $benchmarkClasspath -d build/benchmark-classes tooling/benchmarks/node-cleanup/NodeCleanupBenchmark.java
& "$env:JAVA_HOME/bin/java.exe" "-Dlogback.configurationFile=$benchmarkLog" -Xms1g -Xmx1g -cp "build/benchmark-classes;$benchmarkClasspath" NodeCleanupBenchmark
& "$env:JAVA_HOME/bin/java.exe" "-Dlogback.configurationFile=$benchmarkLog" -Xms1g -Xmx1g -cp "build/benchmark-classes;$benchmarkClasspath" NodeCleanupBenchmark --validate-cleanup
```

Set JAVA_HOME to the configured JDK 25 installation. In a separate checkout of the
baseline SHA, export its runtime classpath with the same init script (an absolute
script path can reference the candidate checkout). Run the same compiled harness
with the baseline classpath and identical JVM arguments. Deterministic cleanup
validation is candidate-only because the baseline lacks permanent destruction.

## Full verification

The following completed successfully with desktop already excluded by repository
settings; desktop restoration remains deferred:

```text
gradlew.bat projects test ktlintCheck build coverageReport :engine:benchmarkClasspath --init-script tooling/benchmarks/node-cleanup/classpath.init.gradle.kts --no-daemon --console=plain
```

Engine: 163 tests pass without skips. Compiler checks and consumer integration are
in a separate stacked PR and must merge before releasing the complete state-safety
system. Runtime class validation remains active in this branch. Runtime-only line coverage is 2,236 / 3,158 (70.8%), above the unchanged 60% gate. Both repository diffs pass `git diff --check`; relative links in the
five changed companion documentation files resolve locally.
