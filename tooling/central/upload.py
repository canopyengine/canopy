#!/usr/bin/env python3
"""Upload a signed bundle for Portal validation; never promote it to a release."""
import argparse
import base64
import json
import os
from pathlib import Path
import urllib.error
import urllib.request
import uuid

ENDPOINT = 'https://central.sonatype.com/api/v1/publisher/upload?publishingType=USER_MANAGED'


def multipart(bundle: Path, boundary: str) -> bytes:
    filename = bundle.name
    if any(character in filename for character in '\r\n"'):
        raise ValueError('Bundle filename contains unsupported characters')
    return (f'--{boundary}\r\nContent-Disposition: form-data; name="bundle"; '
            f'filename="{filename}"\r\nContent-Type: application/zip\r\n\r\n').encode() + bundle.read_bytes() + f'\r\n--{boundary}--\r\n'.encode()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('bundle', type=Path)
    parser.add_argument('--result', type=Path, required=True)
    args = parser.parse_args()
    if not args.bundle.is_file() or args.bundle.suffix != '.zip':
        parser.error('Supply a validated signed ZIP bundle')
    username = os.environ.get('CENTRAL_USERNAME')
    password = os.environ.get('CENTRAL_PASSWORD')
    if not username or not password:
        parser.error('CENTRAL_USERNAME and CENTRAL_PASSWORD are required')
    token = base64.b64encode(f'{username}:{password}'.encode()).decode()
    boundary = 'canopy-' + uuid.uuid4().hex
    request = urllib.request.Request(ENDPOINT, data=multipart(args.bundle, boundary), headers={
        'Authorization': f'Bearer {token}',
        'Content-Type': f'multipart/form-data; boundary={boundary}',
    }, method='POST')
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            deployment_id = response.read().decode().strip().strip('"')
    except urllib.error.HTTPError as error:
        raise SystemExit(f'Central upload failed with HTTP {error.code}; inspect the Portal before retrying') from None
    except urllib.error.URLError:
        raise SystemExit('Central upload connection failed; inspect the Portal before retrying to avoid a duplicate upload') from None
    if not deployment_id or len(deployment_id) > 200 or any(c in deployment_id for c in '\r\n'):
        raise SystemExit('Unexpected Central response; inspect the Portal before retrying')
    args.result.parent.mkdir(parents=True, exist_ok=True)
    args.result.write_text(json.dumps({'deployment_id': deployment_id, 'publishing_type': 'USER_MANAGED',
                                      'published': False}, indent=2) + '\n')
    print('Bundle uploaded for Portal validation. Publication still requires manual approval in Central.')


if __name__ == '__main__':
    main()
