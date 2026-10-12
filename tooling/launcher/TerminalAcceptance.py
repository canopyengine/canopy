"""Unix PTY acceptance against a fresh consumer of the staged Maven artifacts.

Requires Python 3.10+, pyte 0.8.2 and the project's configured JDKs. The terminal
emulator checks visible output; native input and mode restoration use a real PTY.
"""

import argparse
import codecs
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time

if os.name != "posix":
    raise SystemExit("This PTY check needs Unix; LauncherTests.java supports Windows too.")

import fcntl
import pty
import select
import signal
import struct
import termios

try:
    import pyte
except ImportError:
    raise SystemExit("Install the test dependency: python3 -m pip install pyte==0.8.2")


class Session:
    def __init__(self, name, repo, project, output, java, offline):
        self.name = name
        self.output = output
        self.project = project
        self.screen = pyte.Screen(80, 24)
        self.stream = pyte.Stream(self.screen)
        self.decoder = codecs.getincrementaldecoder("utf-8")("replace")
        self.raw = bytearray()
        self.checks = []
        self.master, slave = pty.openpty()
        fcntl.ioctl(slave, termios.TIOCSWINSZ, struct.pack("HHHH", 24, 80, 0, 0))
        self.original = termios.tcgetattr(slave)

        def controlling_terminal():
            os.setsid()
            fcntl.ioctl(slave, termios.TIOCSCTTY, 0)

        command = [
            java, str(repo / "tooling/launcher/CanopyLaunch.java"), "-p", str(project),
            "--gradle-arg", "--max-workers=2",
        ]
        if offline:
            command += ["--gradle-arg", "--offline"]
        try:
            self.child = subprocess.Popen(
                command, stdin=slave, stdout=slave, stderr=slave,
                env=dict(os.environ, TERM="xterm-256color", COLORTERM="truecolor"),
                preexec_fn=controlling_terminal,
            )
        except BaseException:
            os.close(self.master)
            raise
        finally:
            os.close(slave)

    def pump(self, seconds=0.2):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            delay = min(0.1, max(0, deadline - time.monotonic()))
            if select.select([self.master], [], [], delay)[0]:
                try:
                    data = os.read(self.master, 65536)
                except OSError:
                    break
                if not data:
                    break
                self.raw.extend(data)
                self.stream.feed(self.decoder.decode(data))
                (self.output / f"{self.name}.bin").write_bytes(self.raw)

    def text(self):
        return "\n".join(self.screen.display)

    def wait(self, label, predicate, timeout=10):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            self.pump()
            if predicate():
                self.checks.append(label)
                print(f"PASS: {label}", flush=True)
                return
            if self.child.poll() is not None:
                break
        self.save("failure")
        raise AssertionError(
            f"{label}; game exit={self.child.poll()}\n{self.text()}\n"
            f"See {self.output / (self.name + '.bin')}"
        )

    def send(self, value):
        os.write(self.master, value if isinstance(value, bytes) else value.encode())

    def ticks(self):
        found = re.search(r"Ticks: (\d+)", self.text())
        return int(found[1]) if found else -1

    def focused(self, text):
        for y, row in enumerate(self.screen.display):
            start = row.find(text)
            if start >= 0:
                return self.screen.buffer[y][start].reverse
        return False

    def colors(self):
        chars = [cell for row in self.screen.buffer.values() for cell in row.values()]
        rabbit = any(cell.data == "r" and cell.fg == "78f078" for cell in chars)
        fox = any(cell.data == "f" and cell.fg == "ff8c46" for cell in chars)
        return rabbit and fox

    def resize(self, columns, rows):
        self.screen.resize(lines=rows, columns=columns)
        fcntl.ioctl(self.master, termios.TIOCSWINSZ, struct.pack("HHHH", rows, columns, 0, 0))
        self.pump(0.5)

    def save(self, label):
        (self.output / f"{self.name}-{label}.txt").write_text(self.text())
        cells = [
            dict(x=x, y=y, text=cell.data, fg=cell.fg, bg=cell.bg,
                 bold=cell.bold, inverse=cell.reverse)
            for y, row in self.screen.buffer.items() for x, cell in row.items()
            if cell.data.strip() or cell.bg != "default" or cell.reverse
        ]
        (self.output / f"{self.name}-{label}.json").write_text(json.dumps(cells, indent=2))

    def stopped(self):
        self.wait("clean process exit", lambda: self.child.poll() is not None, timeout=15)
        assert self.child.returncode == 0, f"Game exit code: {self.child.returncode}"
        assert b"\x1b[?1049l" in self.raw, "Alternate screen not restored"
        assert termios.tcgetattr(self.master)[:4] == self.original[:4], "Terminal flags not restored"
        self.checks.append("alternate screen and native terminal flags restored")
        result = dict(exit=self.child.returncode, checks=self.checks, bytes=len(self.raw))
        (self.output / f"{self.name}-result.json").write_text(json.dumps(result, indent=2))

    def close(self):
        if self.child.poll() is None:
            os.killpg(self.child.pid, signal.SIGKILL)
            self.child.wait()
        os.close(self.master)
        logs = self.project / ".canopy/logs"
        if logs.exists():
            shutil.copytree(logs, self.output / f"{self.name}-logs", dirs_exist_ok=True)
        for crash in self.project.glob("hs_err_pid*.log"):
            shutil.copy(crash, self.output / f"{self.name}-{crash.name}")


def exercise(session):
    session.wait(
        "styled moving map rendered",
        lambda: "Canopy terminal acceptance" in session.text() and session.colors(), timeout=150,
    )
    first = session.ticks()
    session.wait("normal node updates animate map", lambda: session.ticks() > first + 2)
    session.save("initial")
    session.send(b"\x1b[B")
    session.wait("Down focuses Add rabbit", lambda: session.focused("Add rabbit"))
    session.send("\r")
    session.wait("Enter activates Add rabbit", lambda: "Population: 4" in session.text())
    session.send(b"\x1b[B")
    session.wait("Down focuses Toggle details", lambda: session.focused("Toggle details"))
    session.send("\r")
    session.wait("conditional details clear without stale text", lambda: "Details visible" not in session.text())
    session.save("focused")

    # The prompt owns Escape and retains the underlying button selection.
    session.send(b"\x1b")
    session.wait("Escape opens command editor", lambda: session.screen.display[-1].startswith("> "))
    before = session.ticks()
    session.wait("opening commands preserves simulation updates", lambda: session.ticks() > before + 2)
    session.send("pause\r")
    session.wait("pause command accepted", lambda: "Simulation paused" in session.text())
    session.pump(0.3)
    frozen = session.ticks()
    assert frozen >= 0
    session.pump(0.6)
    assert session.ticks() == frozen, "Paused scene still updating"
    session.checks.append("pause stops scene updates")
    session.save("paused")
    session.send(b"\x1b")
    session.wait("closing prompt restores world", lambda: not session.screen.display[-1].startswith("> "))
    session.pump(0.5)
    assert session.ticks() == frozen, "Closing prompt resumed explicitly paused app"
    session.checks.append("closing prompt preserves explicit pause")
    session.send(b"\x1b[B")
    session.wait("focus remains responsive while paused", lambda: session.focused("Add rabbit"))
    session.send("\r")
    session.wait("button action remains responsive while paused", lambda: "Population: 5" in session.text())

    session.send(b"\x1b")
    session.wait("reopen paused command editor", lambda: session.screen.display[-1].startswith("> "))
    session.send("res")
    session.wait("editor draft captured", lambda: session.screen.display[-1].startswith("> res"))
    for columns, rows, label in [(32, 10, "narrow"), (100, 30, "wide")]:
        session.resize(columns, rows)
        session.wait(
            f"{label} resize preserves editor draft",
            lambda: session.screen.display[-1].startswith("> res") and session.colors(),
        )
        assert session.ticks() == frozen, "Resize resumed paused scene"
        session.save(label)
    session.send("ume\r")
    session.wait(
        "partial draft resumes simulation after resize",
        lambda: "Simulation resumed" in session.text() and session.ticks() > frozen + 2,
    )
    session.save("resumed")
    session.send("quit\r")
    session.stopped()


def create_project(repo, project, version, kotlin_version):
    repository = (repo / "build/central-repository").as_uri()
    shutil.copytree(repo / "gradle/wrapper", project / "gradle/wrapper")
    (project / "settings.gradle.kts").write_text(f'''
pluginManagement {{ repositories {{ maven {{ url = uri("{repository}") }}; gradlePluginPortal(); mavenCentral() }} }}
rootProject.name = "terminal-acceptance"
''')
    (project / "build.gradle.kts").write_text(f'''
plugins {{
    kotlin("jvm") version "{kotlin_version}"
    id("io.github.canopyengine.compiler") version "{version}"
    application
}}
repositories {{ maven {{ url = uri("{repository}") }}; mavenCentral() }}
dependencies {{
    implementation("io.github.canopyengine:engine:{version}")
    implementation("io.github.canopyengine:platforms-terminal:{version}")
}}
kotlin {{ jvmToolchain(25) }}
application {{ mainClass.set("TerminalAcceptanceKt") }}
tasks.named<JavaExec>("run") {{ jvmArgs("--enable-native-access=ALL-UNNAMED") }}
''')
    sources = project / "src/main/kotlin"
    sources.mkdir(parents=True)
    shutil.copy(repo / "tooling/launcher/TerminalAcceptance.kt", sources)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path)
    parser.add_argument("--java", default=str(Path(os.environ["JAVA_HOME"]) / "bin/java")
                        if "JAVA_HOME" in os.environ else "java")
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    repo = args.repo.resolve()
    output = (args.output or repo / "build/terminal-acceptance").resolve()
    output.mkdir(parents=True, exist_ok=True)
    version = re.search(r"(?m)^canopyVersion\s*=\s*(\S+)", (repo / "gradle.properties").read_text())[1]
    kotlin_version = re.search(r'(?m)^kotlin = "([^"]+)"', (repo / "gradle/libs.versions.toml").read_text())[1]
    repository = repo / "build/central-repository/io/github/canopyengine"
    for module in ["engine", "platforms-terminal"]:
        if not (repository / module / version / f"{module}-{version}.jar").exists():
            raise SystemExit("Stage the artifacts first: ./gradlew stageCentralPublication")

    with tempfile.TemporaryDirectory(prefix="canopy terminal acceptance ") as scratch:
        project = Path(scratch)
        create_project(repo, project, version, kotlin_version)
        session = Session("input", repo, project, output, args.java, args.offline)
        try:
            exercise(session)
        finally:
            session.close()
        # Verify the installed distribution actually used these staged artifacts.
        hashes = {}
        for module in ["engine", "platforms-terminal"]:
            staged = repository / module / version / f"{module}-{version}.jar"
            installed = project / "build/install/terminal-acceptance/lib" / staged.name
            digest = hashlib.sha256(staged.read_bytes()).hexdigest()
            assert digest == hashlib.sha256(installed.read_bytes()).hexdigest(), f"Stale {module} artifact"
            hashes[module] = digest
        (output / "artifact-hashes.json").write_text(json.dumps(hashes, indent=2))
        session = Session("ctrl-c", repo, project, output, args.java, args.offline)
        try:
            session.wait(
                "second fresh terminal launch renders",
                lambda: "Canopy terminal acceptance" in session.text() and session.colors(), timeout=150,
            )
            session.send(b"\x03")
            session.stopped()
        finally:
            session.close()
    print(f"All terminal acceptance checks passed. Evidence: {output}", flush=True)


if __name__ == "__main__":
    main()
