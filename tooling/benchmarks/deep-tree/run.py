"""Opt-in isolated JVM samples; never run overflow cases inside the engine test JVM."""
from pathlib import Path
import argparse
import csv
import json
import os
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--java-home", type=Path, required=True, help="JDK 25 home for java and javac")
parser.add_argument("--output", type=Path, default=None)
parser.add_argument("--classpath-file", type=Path, help="Reuse an already built engine runtime classpath")
args = parser.parse_args()
here = Path(__file__).resolve().parent
repo = here.parents[2]
output = args.output.resolve() if args.output else Path(tempfile.mkdtemp(prefix="canopy-deep-tree-"))
output.mkdir(parents=True, exist_ok=True)
java = str(args.java_home.resolve() / "bin/java")
javac = str(args.java_home.resolve() / "bin/javac")
classpath_file = args.classpath_file.resolve() if args.classpath_file else output / "runtime-classpath.txt"
if args.classpath_file is None:
    command = ["bash", str(repo / "gradlew"), "-I", str(here / "classpath.init.gradle.kts"),
               "-DdeepTree.classpathOutput=" + str(classpath_file), ":engine:deepTreeClasspath",
               ":engine:compileKotlin", "--max-workers=2", "--console=plain"]
    result = subprocess.run(command, cwd=repo, capture_output=True, text=True)
    (output / "setup.json").write_text(json.dumps({"command": command, "returncode": result.returncode,
                                                "stdout": result.stdout, "stderr": result.stderr}, indent=2))
    result.check_returncode()
classes = output / "probe-classes"
classes.mkdir(exist_ok=True)
classpath = classpath_file.read_text().strip()
subprocess.run([javac, "-cp", classpath, "-d", str(classes), str(here / "DeepTreeProbe.java")], check=True)
classpath = str(classes) + os.pathsep + classpath
(output / "jvm-version.txt").write_text(subprocess.run([java, "-version"], capture_output=True, text=True).stderr)
operations = ("entry", "ready", "frame", "physics", "input", "exit", "destroy", "detach", "rename")
cases = [(stack, operation, nodes, "chain") for stack in ("256k", "1m")
         for operation in operations for nodes in (128, 512, 2048, 4096)]
cases += [("1m", operation, nodes, "wide") for operation in ("entry", "frame", "exit")
          for nodes in (128, 512, 2048)]
with (output / "results.csv").open("w", newline="") as csv_file, (output / "invocations.jsonl").open("w") as evidence:
    writer = csv.writer(csv_file)
    writer.writerow(["stack", "operation", "nodes", "shape", "status", "stage", "bytes_per_operation"])
    for stack, operation, nodes, shape in cases:
        command = [java, "-Xss" + stack, "-Xmx512m", "-Dlogback.configurationFile=" + str(here / "logback.xml"),
                   "-cp", classpath, "DeepTreeProbe", operation, str(nodes), shape]
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=30)
            record = {"command": command, "returncode": result.returncode,
                      "stdout": result.stdout, "stderr": result.stderr}
            rows = [line.split(",")[1:] for line in result.stdout.splitlines() if line.startswith("RESULT,")]
            if not rows:
                raise RuntimeError("Probe returned no result: " + str(record))
            row = rows[-1]
            expected = 0 if row[3] == "OK" else 2 if row[3] == "STACK_OVERFLOW" else 3
            if result.returncode != expected:
                raise RuntimeError("Probe status/exit code mismatch: " + str(record))
        except subprocess.TimeoutExpired as error:
            record = {"command": command, "returncode": None, "timeoutSeconds": 30,
                      "stdout": str(error.stdout or ""), "stderr": str(error.stderr or "")}
            row = [operation, str(nodes), shape, "TIMEOUT_30S", "UNKNOWN", "0"]
        evidence.write(json.dumps(record) + "\n")
        evidence.flush()
        writer.writerow([stack] + row)
        csv_file.flush()
print("Saved", len(cases), "isolated JVM samples to", output)
