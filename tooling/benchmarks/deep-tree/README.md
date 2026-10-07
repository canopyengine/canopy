# Deep-tree lifecycle samples

This opt-in probe measures recursive traversal limits in isolated JVMs. It is
not a unit-test gate and does not define a supported maximum tree depth.
`DeepTreeLifecycleTests` separately checks 96-node lifecycle, dispatch, pause,
consumption, detach and deferred-destruction contracts in the regular test suite.

Run on Linux or macOS with Bash, Python 3 and JDK 25 for the probe:

```sh
python3 tooling/benchmarks/deep-tree/run.py --java-home /path/to/jdk-25 --output /tmp/canopy-deep-tree
```

The script resolves and compiles the engine with the checked-in Gradle wrapper,
using the environment's Gradle `JAVA_HOME` and configured engine toolchain. It
adds no module or dependency. Omitting `--output` creates a temporary output
directory. `--classpath-file` can reuse a previously exported, compiled engine
runtime classpath. Outputs include CSV, per-invocation command/stdout/stderr/exit
code JSONL, and the actual JVM version. Classpath entries and tool paths reflect
the local machine; no environment-variable dump is collected.

Every operation/depth/stack-size case runs in a fresh JVM with `-Xmx512m`, either
`-Xss256k` or `-Xss1m`, disabled logging, and no tree systems. Overflow terminates
that process; it never contaminates the engine test JVM or another sample. A
30-second process timeout is recorded separately from overflow. The matrix uses
128, 512, 2048 and 4096 total nodes in chains, plus wide trees with 128, 512 and
2048 total nodes. A chain's node count is its depth; wide trees have depth two.

For entry, a detached chain is constructed first. Other phases use incremental
active leaf attachment, avoiding recursive whole-tree entry during setup.
Destruction uses an entered root with `SceneManager.currScene` null, so the
manager update drains deletion without first dispatching the chain. Detachment
removes the root's child subtree; renaming refreshes every descendant path.
`SETUP`, `WARMUP` and `MEASURE` are reported separately so failures can be
attributed to the correct stage.

Frame, physics, input and readiness run 30 warmup traversals followed by 30
measured traversals. Readiness is repeated readiness of already entered nodes,
not their first initialization. Entry, exit, destruction, detach and rename are
single cold operations: their allocation samples can include first-call class
initialization/JIT effects. Construction and attachment are excluded from every
allocation interval. Input also allocates one fresh event per traversal. Paths
use short names; application hooks, systems, real resources and longer paths may
change the results. Allocation samples do not measure retained heap or frame
latency.

Recorded samples are in [results/hotspot-25.0.4.1.csv](results/hotspot-25.0.4.1.csv),
from engine base `51d982b9b6a0041fd7fe84ee8fd92876c5ce1ac8` on Oracle HotSpot
25.0.4.1, Linux, with the stacks above. All 81 isolated processes produced either
success (46) or expected stack overflow (35), with no setup failures, timeouts or
other errors:

| Operation | 256k: largest passing / first overflowing sample | 1m: largest passing / first overflowing sample |
| --- | --- | --- |
| Entry | 512 / 2048 | 2048 / 4096 |
| Ready | 512 / 2048 | 2048 / 4096 |
| Frame | 128 / 512 | 2048 / 4096 |
| Physics | 128 / 512 | 2048 / 4096 |
| Input | 128 / 512 | 2048 / 4096 |
| Exit | 128 / 512 | 512 / 2048 |
| Destroy | 128 / 512 | 2048 / 4096 |
| Detach | 128 / 512 | 512 / 2048 |
| Rename | 512 / 2048 | 2048 / 4096 |

Selected operation-only allocations with `-Xss1m`:

| Operation and shape | Nodes | Bytes per operation | Measurement |
| --- | --- | --- | --- |
| Frame, chain | 2048 | 49,128 | Mean of 30 warmed traversals |
| Frame, wide | 2048 | 32 | Mean of 30 warmed traversals |
| Rename, chain | 128 | 38,728 | One cold operation |
| Rename, chain | 512 | 349,768 | One cold operation |
| Rename, chain | 2048 | 4,542,984 | One cold operation |

Depth and path-prefix growth materially affect these samples. Cold rename
numbers include first-call effects and cannot be compared as warmed frame cost.
No latency or end-to-end performance claim follows from these allocation values.

Separate runs changed some sampled outcomes (including exit, destruction and
rename), reinforcing that these results are environment- and compilation-sensitive.
The table above is derived solely from the recorded final CSV.

These are sampled observations, not exact thresholds or guarantees; JVM
compilation state, stack size and callbacks affect limits. Retrying after an
in-process overflow is unsafe. The probe therefore supplies evidence for future
phase-specific work rather than replacing traversal with one generic walker.
