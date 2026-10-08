# Scene membership rename allocation probe

Opt-in fixture; no Gradle module or dependency additions. Requires Bash, a JDK25
with thread allocation accounting, and the repository's configured Gradle JDKs.
The checked-in samples compare baseline engine commit
`51d982b9b6a0041fd7fe84ee8fd92876c5ce1ac8` with ordered identity membership.

The Java fixture constructs and enters one chain with no systems, then warms 100
root renames. Each of three samples measures 100 more renames between equal-length
names. Construction and entry are excluded. The root remains reachable through
the manager; allocation measurements use the current thread's ThreadMXBean.
Each version runs in a separate JVM with `-Xss2m -Xmx512m`, Oracle JDK 25.0.4.1, logging OFF.

| Chain nodes | Baseline bytes/rename (second sample, repetition1) | Candidate bytes/rename (second sample, repetition1) |
| --- | ---: | ---: |
| 128 |30,184|25,064.56|
| 512 |317,416|296,936|
| 1024 |1,159,144|1,118,184|

Removing per-node path-map reinsertion saves approximately 40 bytes/node in this
fixture. Eager descendant path-string refresh remains, with depth-dependent
allocation. Elapsed times in the raw CSVs vary with JIT/GC; they do not establish
an end-to-end throughput or latency improvement. These results are environment
observations, not supported tree-depth limits.

To reproduce each version from a clean checkout, build its engine, export the
runtime classpath, compile the fixture, and run a fresh JVM. Keep the fixture
outside a baseline checkout if necessary. Set JAVA_HOME to JDK25 for the probe;
Gradle still needs its configured toolchains. Output goes to a temporary directory.

```bash
probe_dir=$(mktemp -d)
cat > "$probe_dir/classpath.gradle" <<'GRADLE'
gradle.projectsEvaluated {
    def engine = gradle.rootProject.findProject(':engine')
    if (engine == null) return
    engine.tasks.register('membershipProbeClasspath') {
        doLast {
            new File(System.getProperty('probe.classpath.output')).text =
                engine.sourceSets.main.runtimeClasspath.asPath
        }
    }
}
GRADLE
bash gradlew :engine:classes :engine:membershipProbeClasspath \
  -I "$probe_dir/classpath.gradle" -Dprobe.classpath.output="$probe_dir/classpath.txt" \
  --max-workers=2 --console=plain
cat > "$probe_dir/logback.xml" <<'XML'
<configuration><root level="OFF"/></configuration>
XML
probe_cp=$(cat "$probe_dir/classpath.txt")
"$JAVA_HOME/bin/javac" -cp "$probe_cp" -d "$probe_dir" \
  tooling/benchmarks/scene-membership/MembershipAllocationProbe.java
"$JAVA_HOME/bin/java" -Xss2m -Xmx512m \
  -Dlogback.configurationFile="$probe_dir/logback.xml" \
  -cp "$probe_dir:$probe_cp" MembershipAllocationProbe > "$probe_dir/results.csv"
cat "$probe_dir/results.csv"
```
