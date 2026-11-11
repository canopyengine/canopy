"""Deterministic checks for the acceptance harness; no PTY or pyte required."""

from pathlib import Path
import contextlib
import io
import json
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock

from TerminalAcceptance import Session, prepare


class WaitTests(unittest.TestCase):
    def session(self, poll):
        session = Session.__new__(Session)
        session.child = SimpleNamespace(poll=poll)
        session.pump = Mock()
        session.save = Mock()
        session.text = lambda: "last frame"
        session.checks = []
        session.name = "exit"
        session.output = Path(".")
        return session

    def test_exit_between_status_checks_satisfies_exit_predicate(self):
        session = self.session(Mock(side_effect=[None, 0, 0]))
        session.wait("clean process exit", lambda: session.child.poll() is not None)
        self.assertEqual(session.checks, ["clean process exit"])
        session.save.assert_not_called()

    def test_exit_does_not_satisfy_a_failed_output_predicate(self):
        session = self.session(Mock(return_value=0))
        with self.assertRaisesRegex(AssertionError, "expected frame; game exit=0"):
            session.wait("expected frame", lambda: False)
        self.assertEqual(session.checks, [])
        session.save.assert_called_once_with("failure")


class PrepareTests(unittest.TestCase):
    def test_prepares_a_persistent_fixture_with_its_repository_launcher(self):
        with tempfile.TemporaryDirectory(prefix="canopy prepare ") as temporary:
            directory = Path(temporary)
            repo = directory / "engine checkout"
            wrapper = repo / "gradle/wrapper"
            wrapper.mkdir(parents=True)
            (wrapper / "gradle-wrapper.jar").write_bytes(b"fixture wrapper")
            fixture = repo / "tooling/launcher/tests/fixtures/TerminalAcceptance.kt"
            fixture.parent.mkdir(parents=True)
            fixture.write_text("fun main() {}", encoding="utf-8")
            output = directory / "evidence"
            output.mkdir()
            project = directory / "terminal manual"

            with contextlib.redirect_stdout(io.StringIO()):
                prepare(repo, project, "0.1.0-dev4", "2.4.10", directory / "repository",
                        output, "java", True, False)

            result = json.loads((output / "prepared-project.json").read_text())
            self.assertFalse(result["built"])
            self.assertEqual(result["command"][1], str(repo / "tooling/launcher/CanopyLaunch.java"))
            self.assertIn("--offline", result["command"])
            self.assertTrue((project / "LAUNCH.txt").is_file())
            self.assertTrue((project / "src/main/kotlin/TerminalAcceptance.kt").is_file())


if __name__ == "__main__":
    unittest.main()
