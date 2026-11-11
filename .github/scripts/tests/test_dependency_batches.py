"""Optional advisory evidence cannot stop unrelated validated dependency work."""

import contextlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

with patch.dict(os.environ, {"GH_REPO": "canopyengine/canopy"}):
    spec = importlib.util.spec_from_file_location(
        "dependency_batches", Path(__file__).resolve().parents[1] / "dependency_batches.py"
    )
    batches = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(batches)


class AdvisoryTests(unittest.TestCase):
    advisory = "GHSA-abcd-1234-efgh"

    def setUp(self):
        batches.severity.cache_clear()
        self.pr = {"body": f"Security details: {self.advisory}"}

    def test_unavailable_advisory_does_not_crash_or_claim_critical_severity(self):
        failure = subprocess.CalledProcessError(1, ["gh", "api"], stderr="HTTP 404")
        output = io.StringIO()
        with patch.object(batches, "api", side_effect=failure) as api, contextlib.redirect_stdout(output):
            self.assertFalse(batches.critical(self.pr))
            self.assertFalse(batches.critical(self.pr))
        self.assertEqual(api.call_count, 1)
        self.assertIn("severity remains unknown", output.getvalue())

    def test_only_confirmed_critical_advisories_get_critical_status(self):
        for severity in ["low", "moderate", "high", "critical"]:
            with self.subTest(severity=severity):
                batches.severity.cache_clear()
                with patch.object(batches, "api", return_value={"severity": severity}):
                    self.assertEqual(batches.critical(self.pr), severity == "critical")

    def test_missing_or_malformed_advisory_metadata_remains_unknown(self):
        for response in [None, {}, {"severity": "unexpected"}]:
            with self.subTest(response=response):
                batches.severity.cache_clear()
                with patch.object(batches, "api", return_value=response), contextlib.redirect_stdout(io.StringIO()):
                    self.assertFalse(batches.critical(self.pr))
