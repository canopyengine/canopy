# Launcher verification

Run commands from the repository root. These tests build separate consumer
projects and launch them as processes; they are independent of Gradle unit tests.
`fixtures/TerminalAcceptance.kt` supplies the small terminal test game.

Run the same integration suite on each operating system:

```sh
java tooling/launcher/tests/LauncherTests.java .
```

The suite creates a real Gradle application and checks root/submodule selection,
custom distribution paths, spaces and shell characters in paths/arguments, empty
and Unicode arguments, Java selection, JVM flags, JPMS, stdin delivery, exit
status, build failures and temporary-file cleanup. With `-Dcanopy.tests.window=true`,
it also verifies a real new console, argument preservation, window/app exit and
cleanup (requires a desktop terminal). Linux CI uses xterm under Xvfb; Windows
CI exercises native console creation. macOS CI compiles the owned-window
AppleScript; actual macOS GUI opening/closing requires a live desktop test.
It requires this repository's
wrapper files and a JDK supported by the pinned Gradle distribution.

The Launcher GitHub Actions workflow runs that command on Linux, Windows and
macOS. CI passes piped input; native terminal controls need an interactive smoke
check on each target system. The terminal starter is suitable for checking arrows,
Enter, Escape, command input, resizing and shutdown.

### Native terminal acceptance

`TerminalAcceptance.py` creates a fresh external game with the matching staged
engine/compiler artifacts and runs it through this launcher in a controlling PTY.
It checks RGB markers, movement, arrow focus, keyboard/mouse button activation, custom wheel input, conditional
clearing, command capture, explicit pause, controls while paused, draft retention
at 80×24 → 32×10 → 100×30, resume, typed quit and Ctrl+C. Both exits must restore
the alternate screen and native terminal flags. Installed JAR hashes must match
the staged artifacts. No composite build or Rust CLI is involved.

The emulator starts with mouse reporting enabled to verify that startup clears
inherited motion modes before enabling UI motion reporting. While commands are
open, injected mouse movement, clicks and wheels must leave both the draft and underlying
UI unchanged. Shutdown disables reporting again. Physical Windows pointer input
is part of the manual checklist.

macOS uses Mordant JNA for input/settings to avoid the undersized native
`termios` buffer in Mordant 3.1.0's FFM backend. Geometry still uses the safe FFM
query, avoiding a `stty` subprocess per frame. macOS CI repeats the full native
fixture five times (ten fresh launches); use `--repeat 5` to reproduce that check.

Use the configured JDKs and Python 3.10+ on Linux or macOS:

```sh
./gradlew stageCentralPublication
python3 -m venv build/terminal-acceptance-venv
build/terminal-acceptance-venv/bin/python -m pip install pyte==0.8.2
build/terminal-acceptance-venv/bin/python tooling/launcher/tests/TerminalAcceptance.py
```

Optional arguments: `--offline` for warm dependency caches, `--java /path/to/java`
for launcher Java selection, `--repo /path/to/canopy`, and `--output /path/to/evidence`.
Raw terminal streams, text/style snapshots, application logs and result JSON go
to `build/terminal-acceptance` by default. This tool's pyte dependency is only for
test-side terminal emulation; it is not an engine or game dependency.

Prepare the same fixture on any OS without pyte or a terminal emulator:

```sh
python tooling/launcher/tests/TerminalAcceptance.py --prepare "build/terminal manual" --build
```

This creates a persistent external game, builds its distribution and verifies
its engine/terminal JAR hashes. It prints a launch command and saves it in the
project's `LAUNCH.txt`. Preparation refuses an existing project directory;
choose a new path to repeat. Omit `--build` to prepare without building. The Java
launcher builds before running either way. `--prepare` requires no Python packages.

The Launcher workflow runs the PTY suite on Linux/macOS and builds this external
consumer on Windows. The Windows build is not native console acceptance:
Python's Unix PTY interface cannot exercise Windows console input. Follow the
[Windows native test](WindowsTerminalAcceptance.md) in a real Windows terminal.
The moving markers are an acceptance fixture, not the full ecosystem simulation.

### Harness unit tests

```sh
python3 -m unittest discover -s tooling/launcher/tests -p 'test_*.py'
```
