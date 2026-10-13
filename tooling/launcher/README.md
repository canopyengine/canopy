# Standalone Java game launcher

Use the same command on Linux, Windows and macOS. Requires a JDK 17+ to run the
launcher and the project's Gradle wrapper JAR/properties. Gradle and the game may
require a newer JDK; the project's configured Java toolchain determines the game
runtime. No Rust, shell script or PowerShell script is required.

Copy `CanopyLaunch.java` into your project, or run it from a Canopy checkout:

```sh
java tooling/launcher/CanopyLaunch.java -p /path/to/game
java tooling/launcher/CanopyLaunch.java -p /path/to/game --module :game -- --smoke
```

From the game directory, `-p` defaults to `.`. Quote paths containing spaces in
your terminal, including Windows paths. Everything after `--` is passed literally
to the game. Before it, `--gradle-arg` supplies one Gradle argument and can repeat:

```sh
java tooling/launcher/CanopyLaunch.java -p /path/to/game --gradle-arg --offline -- --smoke
```

The launcher invokes `gradle/wrapper/gradle-wrapper.jar` with its own Java runtime,
so the project's pinned Gradle distribution is used without going through a shell
or batch file. It creates a temporary Kotlin init script, builds `installDist`,
and reads the game's Java executable, libraries, main class/module, JVM options
and working directory. After Gradle exits, Java starts directly with inherited
stdin/stdout/stderr. Temporary build files are removed before the game starts;
the game exit code is returned to the caller.

Both classpath and JPMS applications are supported. JVM options come from
`application.applicationDefaultJvmArgs` and the Gradle `run` task's `jvmArgs`.
Shell wrapper customizations and `JAVA_OPTS`/`GRADLE_OPTS` are not evaluated; pass
Gradle flags through `--gradle-arg` or use project `gradle.properties`. Custom
application start-script logic and `run` task environment overrides are not
executed. The game runs from its module directory and uses `installDist/lib`.

## Verification

Run the same integration suite on each operating system:

```sh
java tooling/launcher/LauncherTests.java .
```

The suite creates a real Gradle application and checks root/submodule selection,
custom distribution paths, spaces and shell characters in paths/arguments, empty
and Unicode arguments, Java selection, JVM flags, JPMS, stdin delivery, exit
status, build failures and temporary-file cleanup. It requires this repository's
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
inherited motion modes before enabling click/wheel reports. While commands are
open, injected mouse clicks and wheels must leave both the draft and underlying
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
build/terminal-acceptance-venv/bin/python tooling/launcher/TerminalAcceptance.py
```

Optional arguments: `--offline` for warm dependency caches, `--java /path/to/java`
for launcher Java selection, `--repo /path/to/canopy`, and `--output /path/to/evidence`.
Raw terminal streams, text/style snapshots, application logs and result JSON go
to `build/terminal-acceptance` by default. This tool's pyte dependency is only for
test-side terminal emulation; it is not an engine or game dependency.

Prepare the same fixture on any OS without pyte or a terminal emulator:

```sh
python tooling/launcher/TerminalAcceptance.py --prepare "build/terminal manual" --build
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
