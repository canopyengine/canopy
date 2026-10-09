"""Validate missing-artifact rejection and ZIP checksums without publishing."""
import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile

import bundle

ARTIFACTS = ('engine', 'platforms-headless', 'platforms-terminal', 'adapters-libgdx',
             'adapters-logback', 'adapters-mordant', 'tooling-devtools', 'tooling-utils',
             'canopy-compiler', 'canopy-compiler-gradle')
VERSION = '0.1.0-alpha.1'


def fixture(root):
    coordinates = [('io.github.canopyengine', name) for name in ARTIFACTS]
    coordinates.append(('io.github.canopyengine.compiler', 'io.github.canopyengine.compiler.gradle.plugin'))
    for group, artifact in coordinates:
        directory = root / group.replace('.', '/') / artifact / VERSION
        directory.mkdir(parents=True)
        stem = artifact + '-' + VERSION
        pom_only = artifact.endswith('.gradle.plugin')
        packaging = '<packaging>pom</packaging>' if pom_only else ''
        (directory / (stem + '.pom')).write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
            f'<groupId>{group}</groupId><artifactId>{artifact}</artifactId><version>{VERSION}</version>{packaging}'
            '<name>Fixture</name><description>Fixture</description><url>https://example.invalid</url>'
            '<licenses><license><name>Test</name><url>https://example.invalid/license</url></license></licenses>'
            '<developers><developer><name>Test</name><url>https://example.invalid/developer</url></developer></developers>'
            '<scm><connection>scm:git:test</connection><developerConnection>scm:git:test</developerConnection>'
            '<url>https://example.invalid/scm</url></scm></project>')
        if not pom_only:
            for suffix, filename in [('.jar', 'Example.class'), ('-sources.jar', 'Example.kt'), ('-javadoc.jar', 'index.html')]:
                with zipfile.ZipFile(directory / (stem + suffix), 'w') as archive:
                    archive.writestr(filename, 'Fixture content')


class BundleTests(unittest.TestCase):
    def test_expected_publications_and_checksums(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root / 'repository')
            output = root / 'bundle.zip'
            self.assertEqual(bundle.bundle(root / 'repository', output, VERSION, False), 11)
            with zipfile.ZipFile(output) as archive:
                for name in archive.namelist():
                    if name.endswith('.sha256'):
                        self.assertEqual(archive.read(name).decode(), hashlib.sha256(archive.read(name[:-7])).hexdigest())

    def test_required_signatures_reject_unsigned_repository(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root / 'repository')
            with self.assertRaisesRegex(ValueError, 'Missing PGP signature'):
                bundle.bundle(root / 'repository', root / 'bundle.zip', VERSION, True)

    def test_missing_sources_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root / 'repository')
            next((root / 'repository').rglob('*-sources.jar')).unlink()
            with self.assertRaisesRegex(ValueError, 'Missing artifact'):
                bundle.bundle(root / 'repository', root / 'bundle.zip', VERSION, False)

    def test_wrong_version_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root / 'repository')
            with self.assertRaisesRegex(ValueError, 'Unexpected or mutable version'):
                bundle.bundle(root / 'repository', root / 'bundle.zip', '0.1.0-alpha.2', False)

    def test_missing_publication_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root / 'repository')
            next((root / 'repository').rglob('engine-*.pom')).unlink()
            with self.assertRaisesRegex(ValueError, 'Publication set differs'):
                bundle.bundle(root / 'repository', root / 'bundle.zip', VERSION, False)


if __name__ == '__main__':
    unittest.main()
