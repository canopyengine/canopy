# Development tooling

| Directory | Purpose | Tests |
|---|---|---|
| `launcher/` | Java launcher that users can copy into their game project | [`launcher/tests/`](launcher/tests/README.md): process-level integration tests and terminal fixtures |
| `central/` | Maven Central bundle validation and upload tools | `central/tests/`: offline Python regression tests |
| `compiler/` | Canopy compiler and Gradle plugin | `compiler/src/test/`: Kotlin and external-consumer tests |
| `devtools/` | Published helpers for testing user applications | `devtools/src/test/`: tests of those helpers |
| `utils/` | Shared tooling utilities | No separate test source set currently |
| `benchmarks/` | Performance probes, instructions and recorded measurements | Kept together as benchmarks rather than correctness tests |

Module unit tests use the normal Gradle `src/test/` layout. Standalone tools keep
regression suites in a local `tests/` folder; application source used only by a
suite belongs in `tests/fixtures/`. Generated projects, logs and results go under
`build/` by default.

`devtools/src/main/` intentionally contains public testing APIs such as
`AppTestDriver`, `TestAssetEntry` and `InMemoryAssetEntry`. They are library code
that users can depend on, so moving them into `src/test/` would remove them from
the published artifact.

Run the standalone Python suites from the repository root:

```sh
python3 -m unittest discover -s tooling/central/tests -p 'test_*.py'
python3 -m unittest discover -s tooling/launcher/tests -p 'test_*.py'
```

See each tool's README for its integration or benchmark commands.
