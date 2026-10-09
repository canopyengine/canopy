#!/usr/bin/env python3
"""Validate a local Maven layout and create a Central Portal upload bundle."""
import argparse
import hashlib
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def bundle(repository: Path, destination: Path, version: str, require_signatures: bool) -> int:
    poms = sorted(repository.rglob("*.pom"))
    if not poms:
        raise ValueError("No staged Maven publications found")
    files = []
    coordinates = set()
    for pom in poms:
        root = ET.parse(pom).getroot()
        value = lambda key: root.findtext(f"m:{key}", namespaces=NS)
        group, artifact, actual_version = value("groupId"), value("artifactId"), value("version")
        if not group or not group.startswith("io.github.canopyengine"):
            raise ValueError(f"Unverified namespace in {pom}")
        if actual_version != version or version.endswith("SNAPSHOT"):
            raise ValueError(f"Unexpected or mutable version in {pom}")
        coordinate = (group, artifact, version)
        if coordinate in coordinates:
            raise ValueError(f"Duplicate coordinate {coordinate}")
        coordinates.add(coordinate)
        for field in ("name", "description", "url", "licenses", "developers", "scm"):
            if root.find(f"m:{field}", NS) is None:
                raise ValueError(f"Missing {field} in {pom}")
        for field in ("name", "description", "url"):
            if not (value(field) or "").strip():
                raise ValueError(f"Empty {field} in {pom}")
        for section, item, required in (
            ("licenses", "license", ("name", "url")),
            ("developers", "developer", ("name", "url")),
            ("scm", None, ("connection", "developerConnection", "url")),
        ):
            entry = root.find(f"m:{section}" + (f"/m:{item}" if item else ""), NS)
            if entry is None or any(not (entry.findtext(f"m:{key}", namespaces=NS) or "").strip() for key in required):
                raise ValueError(f"Incomplete {section} metadata in {pom}")
        expected_dir = repository / group.replace(".", "/") / artifact / version
        if pom.parent != expected_dir:
            raise ValueError(f"Unexpected Maven layout: {pom}")
        prefix = f"{artifact}-{version}"
        artifacts = [pom]
        if value("packaging") != "pom":
            for suffix in (".jar", "-sources.jar", "-javadoc.jar"):
                item = pom.parent / (prefix + suffix)
                if not item.is_file():
                    raise ValueError(f"Missing artifact {item}")
                with zipfile.ZipFile(item) as archive:
                    members = archive.namelist()
                    if suffix == "-javadoc.jar" and not any(name.endswith(".html") for name in members):
                        raise ValueError(f"No generated HTML documentation in {item}")
                    if suffix == "-sources.jar" and not any(name.endswith((".kt", ".java")) for name in members):
                        raise ValueError(f"No source code in {item}")
                artifacts.append(item)
        module = pom.parent / (prefix + ".module")
        if module.is_file():
            artifacts.append(module)
        for item in artifacts:
            files.append(item)
            signature = item.with_name(item.name + ".asc")
            if require_signatures and not signature.is_file():
                raise ValueError(f"Missing PGP signature {signature}")
            if signature.is_file():
                files.append(signature)
    artifacts = {
        "engine", "platforms-headless", "platforms-terminal", "adapters-libgdx",
        "adapters-logback", "adapters-mordant", "tooling-devtools", "tooling-utils",
        "canopy-compiler", "canopy-compiler-gradle",
    }
    expected = {("io.github.canopyengine", artifact, version) for artifact in artifacts}
    expected.add(("io.github.canopyengine.compiler", "io.github.canopyengine.compiler.gradle.plugin", version))
    if coordinates != expected:
        raise ValueError(f"Publication set differs: missing={expected - coordinates}, unexpected={coordinates - expected}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive:
        for item in sorted(files):
            data = item.read_bytes()
            name = item.relative_to(repository).as_posix()
            archive.writestr(name, data)
            # Central requires checksums alongside deployed artifacts and signatures.
            for algorithm in ("md5", "sha1", "sha256", "sha512"):
                digest = hashlib.new(algorithm, data).hexdigest()
                archive.writestr(name + "." + algorithm, digest)
    return len(coordinates)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path("build/central-repository"))
    parser.add_argument("--output", type=Path, default=Path("build/central-bundle.zip"))
    parser.add_argument("--version", required=True)
    parser.add_argument("--require-signatures", action="store_true")
    args = parser.parse_args()
    try:
        count = bundle(args.repository, args.output, args.version, args.require_signatures)
    except (ValueError, ET.ParseError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Bundle validation failed: {error}\n")
    print(f"Validated {count} publications: {args.output}")
