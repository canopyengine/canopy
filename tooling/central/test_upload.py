"""Offline regression checks: never contact Central or use real credentials."""
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

import upload


class UploadTests(unittest.TestCase):
    def test_manual_upload_records_deployment_without_publishing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            bundle = root / 'bundle.zip'
            bundle.write_bytes(b'test-bundle')
            result = root / 'result.json'
            response = io.BytesIO(b'example-deployment-id')
            output = io.StringIO()
            with patch.dict(os.environ, {'CENTRAL_USERNAME': 'test-user', 'CENTRAL_PASSWORD': 'test-password'}), \
                 patch('sys.argv', ['upload.py', str(bundle), '--result', str(result)]), \
                 patch('upload.urllib.request.urlopen', return_value=response) as send, \
                 contextlib.redirect_stdout(output):
                upload.main()
            request = send.call_args.args[0]
            self.assertIn('publishingType=USER_MANAGED', request.full_url)
            self.assertNotIn('AUTOMATIC', request.full_url)
            self.assertEqual(request.method, 'POST')
            self.assertTrue(request.get_header('Authorization').startswith('Bearer '))
            self.assertIn(b'name="bundle"', request.data)
            self.assertIn(b'test-bundle', request.data)
            self.assertEqual(json.loads(result.read_text())['published'], False)
            self.assertNotIn('test-password', output.getvalue())

    def test_http_error_does_not_print_response_body_or_credentials(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / 'bundle.zip'
            bundle.write_bytes(b'test')
            error = urllib.error.HTTPError(upload.ENDPOINT, 401, 'Unauthorized', {}, io.BytesIO(b'sensitive-response'))
            with patch.dict(os.environ, {'CENTRAL_USERNAME': 'test-user', 'CENTRAL_PASSWORD': 'test-password'}), \
                 patch('sys.argv', ['upload.py', str(bundle), '--result', str(Path(tmp) / 'result.json')]), \
                 patch('upload.urllib.request.urlopen', side_effect=error), \
                 self.assertRaises(SystemExit) as caught:
                upload.main()
            self.assertIn('401', str(caught.exception))
            self.assertNotIn('sensitive-response', str(caught.exception))
            self.assertNotIn('test-password', str(caught.exception))

    def test_missing_credentials_never_sends_request(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / 'bundle.zip'
            bundle.write_bytes(b'test')
            with patch.dict(os.environ, {}, clear=True), \
                 patch('sys.argv', ['upload.py', str(bundle), '--result', str(Path(tmp) / 'result.json')]), \
                 patch('upload.urllib.request.urlopen') as send, \
                 contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                upload.main()
            send.assert_not_called()

    def test_invalid_filename_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'bad"filename.zip'
            path.write_bytes(b'test')
            with self.assertRaises(ValueError):
                upload.multipart(path, 'test-boundary')


if __name__ == '__main__':
    unittest.main()
