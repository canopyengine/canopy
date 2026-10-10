"""POSIX launcher contract checks; the Gradle boundary is a local fixture.

Run with python3 tooling/launcher/test_launcher.py. Real Gradle integration is
verified separately against a Gradle application project.
"""
import os
from pathlib import Path
import select
import subprocess
import tempfile
import unittest


LAUNCHER = Path(__file__).with_name("launch.sh")


class LauncherTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="canopy launcher ")
        self.addCleanup(self.temp.cleanup)
        self.project = Path(self.temp.name)
        self.env = dict(os.environ, TMPDIR=str(self.project))
        (self.project / "gradlew").write_text('''#!/bin/sh
for arg in "$@"; do
    case "$arg" in -PcanopyLaunchManifest=*) manifest=${arg#*=} ;; esac
done
printf '%s\\n' "$@" > gradle-args
printf '%s\\n' "$PWD" "$PWD/java home" "$PWD/game start.sh" > "$manifest"
''')
        (self.project / "game start.sh").write_text('''#!/bin/sh
printf 'HOME=%s\\n' "$JAVA_HOME"
printf 'ARG=<%s>\\n' "$@"
if [ -t 0 ]; then echo TTY=yes; fi
IFS= read -r input
printf 'INPUT=%s\\n' "$input"
exit 7
''')

    def command(self, *args):
        return ["sh", str(LAUNCHER), "-p", str(self.project), *args]

    def test_paths_arguments_input_and_exit_status(self):
        result = subprocess.run(
            self.command("--module", ":game", "--", "two words", "& literal", "--flag"),
            input="keyboard\n", text=True, capture_output=True, env=self.env,
        )
        self.assertEqual(result.returncode, 7, result.stderr)
        for expected in ("ARG=<two words>", "ARG=<& literal>", "ARG=<--flag>", "INPUT=keyboard"):
            self.assertIn(expected, result.stdout)
        self.assertIn(f"HOME={self.project}/java home", result.stdout)
        self.assertIn(":game:canopyLaunchManifest", (self.project / "gradle-args").read_text())
        self.assertEqual(list(self.project.glob("canopy-launch.*")), [])

    def test_build_failure_does_not_start_game_and_removes_manifest(self):
        (self.project / "gradlew").write_text("exit 9\n")
        result = subprocess.run(self.command(), capture_output=True, env=self.env)
        self.assertEqual(result.returncode, 9)
        self.assertEqual(result.stdout, b"")
        self.assertEqual(list(self.project.glob("canopy-launch.*")), [])

    def test_invalid_module_is_rejected_before_build(self):
        for module in ("game", ":game:", ":a::b", ":game;echo bad"):
            with self.subTest(module=module):
                result = subprocess.run(self.command("--module", module), capture_output=True, env=self.env)
                self.assertEqual(result.returncode, 2)
        self.assertFalse((self.project / "gradle-args").exists())

    def test_real_terminal_is_inherited(self):
        master, slave = os.openpty()
        process = subprocess.Popen(self.command(), stdin=slave, stdout=slave, stderr=slave, env=self.env)
        os.close(slave)
        try:
            os.write(master, b"keyboard\n")
            data = bytearray()
            while select.select([master], [], [], 10)[0]:
                try:
                    chunk = os.read(master, 8192)
                except OSError:
                    break
                if not chunk:
                    break
                data.extend(chunk)
            self.assertEqual(process.wait(timeout=5), 7)
            self.assertIn(b"TTY=yes", data)
            self.assertIn(b"INPUT=keyboard", data)
        finally:
            os.close(master)
            if process.poll() is None:
                process.kill()
                process.wait()


if __name__ == "__main__":
    unittest.main()
