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
