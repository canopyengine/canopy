# Windows native terminal acceptance

Run this against the foundation integration branch in Windows Terminal with
PowerShell, then repeat in the Windows Console Host if supporting it. Record the
Windows version, terminal version, engine commit and JDK version. CI builds the
same external fixture on Windows but does not perform these interactive checks.

Use Python 3.10+ and the configured JDKs (17 and 25); select JDK 25 for `java`.
From the Canopy repository, stage matching artifacts and prepare the fixture:

```powershell
.\gradlew.bat stageCentralPublication
python tooling/launcher/TerminalAcceptance.py --prepare 'build/terminal manual' --build
```

No Python packages or source copying are needed. Preparation refuses an existing
directory; choose another path for a fresh fixture. The generated project's
`LAUNCH.txt` contains its command. `build/terminal-acceptance/prepared-build.log`
and `artifact-hashes.json` record the build and staged-artifact identity.

Load this small probe once in the same PowerShell session. It reads the console
input/output mode words, without changing them:

```powershell
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class CanopyConsoleProbe {
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern IntPtr GetStdHandle(int handle);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool GetConsoleMode(IntPtr handle, out uint mode);
    public static string Modes() {
        uint input, output;
        if (!GetConsoleMode(GetStdHandle(-10), out input) ||
            !GetConsoleMode(GetStdHandle(-11), out output))
            throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
        return input.ToString("X8") + ":" + output.ToString("X8");
    }
}
'@
$before = [CanopyConsoleProbe]::Modes()
java tooling/launcher/CanopyLaunch.java -p 'build/terminal manual'
$gameExit = $LASTEXITCODE
$after = [CanopyConsoleProbe]::Modes()
if ($gameExit -ne 0) { throw "Game exit: $gameExit" }
if ($before -ne $after) { throw "Console modes changed: $before -> $after" }
```

The launcher must inherit the actual console: do not pipe or redirect its output
or run it through Gradle's `run` task. While the game runs, perform these checks:

| Action | Expected result |
| --- | --- |
| Observe the first frame | Green `r` and orange `f` move; ticks increase; population is 3. |
| Down, Enter | Add rabbit is visibly selected; population becomes 4. |
| Down, Enter | Toggle details is selected; the details line disappears cleanly. |
| Escape | The command editor opens; ticks keep increasing. |
| Move, click and drag the mouse with the editor open | The draft stays unchanged; no mouse-report characters appear. |
| Type `pause`, Enter | Pause is reported and ticks stop. |
| Escape | The editor closes; the simulation stays paused. |
| Down, Enter | Add rabbit is selected and population becomes 5 while ticks stay frozen. |
| Escape, type `res` | The editor retains the partial draft. |
| Resize to roughly 32×10, then 100×30 | Draft, marker colors and pause survive; borders/text clear and repaint without leftovers. |
| Type `ume`, Enter | `resume` is accepted; ticks and movement continue. |
| Type `quit`, Enter | Exit is zero; the previous screen and console mode words return. |

Run the probe/launcher block a second time. Wait for the first frame, then press
Ctrl+C. Require exit zero, the previous screen, equal console modes and normal
typing/echo at the shell. This separate run checks cancellation during native
input, not just the command-driven exit path.

Copy this result form into a test report, attach screenshots for initial,
focused, narrow and resumed frames, and copy the fixture's `.canopy/logs` before
another run replaces them:

```text
Engine commit:
Windows / terminal / JDK versions:
Build and artifact hash check: PASS / FAIL
Colors, movement, focus, actions, conditional clearing: PASS / FAIL
Command input, pause, paused controls: PASS / FAIL
Narrow/wide resize, retained draft, resume: PASS / FAIL
Typed quit: exit= ; console modes before= ; after=
Ctrl+C: exit= ; console modes before= ; after=
Previous screen and normal shell input restored: PASS / FAIL
Logs / screenshots / failure details:
```

A completed successful report establishes acceptance for that terminal/Windows
combination. A passing build alone does not establish native input or rendering.
