"""Deterministic checks for the acceptance harness; no PTY or pyte required."""

from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import Mock

from TerminalAcceptance import Session


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


if __name__ == "__main__":
    unittest.main()
