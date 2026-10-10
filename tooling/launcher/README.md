# Standalone game launcher

Copy this folder into a Gradle application project, or run it from a Canopy
checkout. Requires the project's Gradle wrapper and Java; no CLI installation
or Rust toolchain is needed.

From your game project on Linux/macOS:

```sh
sh /path/to/canopy/tooling/launcher/launch.sh
```

Or choose the project and application submodule:

```sh
sh tooling/launcher/launch.sh -p /path/to/game --module :game -- --smoke
```

On Windows, use PowerShell:

```powershell
& C:\path\to\canopy\tooling\launcher\launch.ps1 -Project C:\path\to\game
```

Select a submodule with `-Module :game`; pass application arguments with
`-GameArgs @('--smoke')`. PowerShell's local script execution policy must permit
the script to run.

The launcher runs `installDist`, waits for Gradle to exit, and invokes the
generated application start script with the original console handles and the
application's Java toolchain. It discovers the configured distribution path and
application name, supports paths with spaces, and returns the application's exit
code. Arguments after `--` in the shell launcher belong to the game.

Set persistent JVM options in `application.applicationDefaultJvmArgs`, as for a
normal Gradle distribution. Options configured only on the Gradle `run` task do
not change the installed start scripts. The game runs from its module directory.
Custom distributions must retain the application plugin's installed start script.
Windows/macOS execution has not yet been verified.
