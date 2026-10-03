"""Stage critical fixes and merge checked dependency PRs; main is human-only."""

import json
import os
import re
import subprocess
from functools import cache

REPO = os.environ["GH_REPO"]
BASE = "dependency-updates"
THRESHOLD = 5
WEEKLY = os.environ.get("EVENT_NAME") == "workflow_dispatch" or os.environ.get(
    "EVENT_SCHEDULE"
) == "0 8 * * 1"
STAGING_PREFIX = "codex/agent/bug/stage-critical-"
QUEUE_LABEL = "dependency-auto-merge"
SOURCE_MARKER = re.compile(r"<!-- dependency-source: (\d+) ([0-9a-f]{40}) -->")


def api(path, method="GET", data=None):
    """Use the runner's scoped token; never interpolate PR data into shell code."""
    command = ["gh", "api", path, "--method", method]
    if data is not None:
        command += ["--input", "-"]
    result = subprocess.run(
        command, input=json.dumps(data) if data is not None else None,
        capture_output=True, text=True, check=True,
    )
    return json.loads(result.stdout) if result.stdout.strip() else None


def pages(path):
    result = []
    separator = "&" if "?" in path else "?"
    for page in range(1, 1000):
        batch = api(f"{path}{separator}per_page=100&page={page}")
        result.extend(batch)
        if len(batch) < 100:
            return result
    raise RuntimeError("Pagination limit reached; refusing an incomplete batch")


def pulls(state, base):
    return pages(f"repos/{REPO}/pulls?state={state}&base={base}")


def tip(branch):
    return api(f"repos/{REPO}/git/ref/heads/{branch}")["object"]["sha"]


def contains(base, commit):
    comparison = api(f"repos/{REPO}/compare/{base}...{commit}")
    return comparison["status"] in ("identical", "behind")


@cache
def severity(advisory):
    return api(f"advisories/{advisory}")["severity"]


def advisories(pr):
    return set(re.findall(r"GHSA-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}", pr.get("body") or ""))


def critical(pr):
    """Use GitHub advisory severity, not PR titles or user-applied labels."""
    return any(severity(advisory) == "critical" for advisory in advisories(pr))


def source(pr):
    if pr["user"]["login"] == "dependabot[bot]":
        return pr
    marker = SOURCE_MARKER.search(pr.get("body") or "")
    if pr["user"]["login"] != "github-actions[bot]" or not marker:
        return None
    if not pr["head"]["ref"].startswith(STAGING_PREFIX):
        return None
    original = api(f"repos/{REPO}/pulls/{marker[1]}")
    if original["user"]["login"] != "dependabot[bot]":
        return None
    if not pr.get("merged_at") and original["head"]["sha"] != marker[2]:
        return None  # An updated security fix needs a fresh staging snapshot.
    return original if critical(original) or (pr.get("merged_at") and critical(pr)) else None


def labels(number):
    api(f"repos/{REPO}/issues/{number}/labels", "POST", {
        "labels": ["pull-request", "agent-originated", "dependencies", "operations", "ci"]
    })


def dispatch(branch):
    for workflow in ("build.yml", "codeql.yml"):
        api(f"repos/{REPO}/actions/workflows/{workflow}/dispatches", "POST", {"ref": branch})


def checks_pass(pr):
    """Require current-head CI, successful non-skipped checks, and no pending statuses."""
    checks = []
    for page in range(1, 1000):
        batch = api(f"repos/{REPO}/commits/{pr['head']['sha']}/check-runs?filter=latest&per_page=100&page={page}")
        checks.extend(batch["check_runs"])
        if len(batch["check_runs"]) < 100:
            break
    else:
        return False
    status = api(f"repos/{REPO}/commits/{pr['head']['sha']}/status")
    required = ("Gradle build", "Analyze (CodeQL)")
    if any(not any(check["name"].startswith(name) for check in checks) for name in required):
        dispatch(pr["head"]["ref"])
        return False
    for name in required:
        if not any(
            check["name"].startswith(name) and check["app"]["slug"] == "github-actions"
            and check["status"] == "completed" and check["conclusion"] == "success"
            for check in checks
        ):
            return False
    # This main-only publishing job is deliberately skipped on dependency CI.
    checks = [check for check in checks if not (
        check["name"] == "Dependency submission" and check["conclusion"] == "skipped"
        and check["app"]["slug"] == "github-actions"
    )]
    if any(check["status"] != "completed" or check["conclusion"] != "success" for check in checks):
        return False
    # Combined status paginates at 100 by default; inspect every latest context.
    statuses = pages(f"repos/{REPO}/commits/{pr['head']['sha']}/statuses")
    latest = {}
    for item in statuses:
        latest.setdefault(item["context"], item["state"])
    return (not status["total_count"] or status["state"] == "success") and all(
        value == "success" for value in latest.values()
    )


def stage_critical(open_dependencies):
    for pr in pulls("open", "main"):
        if pr["draft"] or pr["user"]["login"] != "dependabot[bot]" or not critical(pr):
            continue
        sha = pr["head"]["sha"]
        marker = f"<!-- dependency-source: {pr['number']} {sha} -->"
        if any(marker in (item.get("body") or "") for item in open_dependencies):
            continue
        branch = f"{STAGING_PREFIX}{pr['number']}-{sha[:12]}"
        # An existing snapshot may belong to a closed PR; do not create duplicate staging.
        existing = pages(f"repos/{REPO}/pulls?state=all&head={REPO.split('/')[0]}:{branch}")
        if existing:
            continue
        try:
            api(f"repos/{REPO}/git/refs", "POST", {"ref": f"refs/heads/{branch}", "sha": sha})
        except subprocess.CalledProcessError:
            if tip(branch) != sha:
                raise
        staged = api(f"repos/{REPO}/pulls", "POST", {
            "base": BASE, "head": branch,
            "title": f"[Agent] Stage critical security fix from #{pr['number']}",
            "body": f"## Summary\nAgent-originated contribution prepared by GitHub Actions.\n\n"
                    f"Stage the critical security update from {pr['html_url']} at `{sha}`.\n\n"
                    "## Changes\nCopy the Dependabot revision without changing the original main-target PR.\n\n"
                    "## Related Issues\nOriginal security PR remains available for human review.\n\n"
                    "## How to Test\nBuild and CodeQL are explicitly dispatched for this snapshot.\n"
                    "Only successful current-revision checks permit automatic merging into dependency-updates.\n\n"
                    f"Critical advisory evidence: {', '.join(sorted(advisories(pr)))}\n\n"
                    f"{marker}",
        })
        labels(staged["number"])
        dispatch(branch)
        print(f"Staged critical fix #{pr['number']} as #{staged['number']}")


def merge_dependencies():
    changed = False
    for item in pulls("open", BASE):
        pr = api(f"repos/{REPO}/pulls/{item['number']}")
        original = source(pr)
        if not original or pr["draft"] or pr["head"]["repo"]["full_name"] != REPO:
            continue
        queued = any(label["name"] == QUEUE_LABEL for label in pr["labels"])
        if WEEKLY and not queued:
            api(f"repos/{REPO}/issues/{pr['number']}/labels", "POST", {"labels": [QUEUE_LABEL]})
            queued = True
        if not queued and not critical(original):
            continue
        base_sha = tip(BASE)
        if not contains(pr["head"]["sha"], base_sha):
            # Updating with GITHUB_TOKEN does not trigger push CI; dispatch it ourselves.
            try:
                api(f"repos/{REPO}/pulls/{pr['number']}/update-branch", "PUT", {
                    "expected_head_sha": pr["head"]["sha"]
                })
            except subprocess.CalledProcessError as error:
                print(f"::warning::Unable to update #{pr['number']}: {error.stderr.strip()}")
                continue
            dispatch(pr["head"]["ref"])
            print(f"Updated #{pr['number']}; waiting for fresh checks")
            continue
        if not pr["mergeable"] or not checks_pass(pr):
            print(f"Skipping #{pr['number']}: incomplete or unsuccessful checks, or not mergeable")
            continue
        fresh = api(f"repos/{REPO}/pulls/{pr['number']}")
        if fresh["base"]["ref"] != BASE or fresh["head"]["sha"] != pr["head"]["sha"] or tip(BASE) != base_sha:
            continue
        # Only dependency-target PRs can be merged; no approval reviews are submitted.
        try:
            result = api(f"repos/{REPO}/pulls/{pr['number']}/merge", "PUT", {
                "sha": pr["head"]["sha"], "merge_method": "squash",
                "commit_title": f"[Agent] Merge dependency update #{pr['number']}",
                "commit_message": "Automated checked dependency integration.\n\n"
                                  "Agent-Originated: true\nAgent: GitHub Actions",
            })
        except subprocess.CalledProcessError as error:
            print(f"::warning::Unable to merge #{pr['number']}: {error.stderr.strip()}")
            continue
        changed |= result["merged"]
        print(f"Dependency PR #{pr['number']}: merged={result['merged']}")
    if changed:
        dispatch(BASE)


def integration():
    completed = [pr for pr in pulls("closed", "main") if pr["merged_at"] and pr["head"]["ref"] == BASE]
    last = max(completed, key=lambda pr: pr["merged_at"], default=None)
    # Previous integration head preserves accounting even when humans squash-merge to main.
    baseline = last["head"]["sha"] if last else tip("main")
    pending = []
    for pr in pulls("closed", BASE):
        if pr["merged_at"] and source(pr) and not contains(baseline, pr["merge_commit_sha"]):
            pending.append(pr)
    urgent = any(critical(pr) or critical(source(pr)) for pr in pending)
    existing = [pr for pr in pulls("open", "main") if pr["head"]["ref"] == BASE]
    if not pending or (not existing and len(pending) < THRESHOLD and not urgent):
        return
    comparison = api(f"repos/{REPO}/compare/main...{BASE}")
    if not comparison["files"]:
        return
    body = "## Summary\nAgent-originated contribution prepared by GitHub Actions.\n\n"
    body += f"Integrate {len(pending)} dependency PRs" + (" including a critical security fix.\n" if urgent else ".\n")
    body += "\n## Changes\n" + "\n".join(f"- #{pr['number']}: {pr['title']}" for pr in pending)
    body += "\n\n## Related Issues\nSee the dependency PRs above.\n\n## How to Test\n"
    body += "Review Build and CodeQL on the current integration revision and inspect upstream release notes.\n\n"
    body += "**Only humans may approve and merge this PR into main. "
    body += "This automation never approves it or enables auto-merge.**"
    if existing:
        # Preserve human-authored integration descriptions and review notes.
        if existing[0]["user"]["login"] == "github-actions[bot]" and existing[0].get("body") != body:
            api(f"repos/{REPO}/pulls/{existing[0]['number']}", "PATCH", {"body": body})
        return
    pr = api(f"repos/{REPO}/pulls", "POST", {
        "base": "main", "head": BASE, "title": "[Agent] Integrate dependency updates", "body": body,
    })
    labels(pr["number"])
    # Explicit dispatch avoids needing a PAT to run CI after token-created PR events.
    dispatch(BASE)
    print(f"Created human-only integration PR #{pr['number']}")


def main():
    try:
        api(f"repos/{REPO}/labels/{QUEUE_LABEL}")
    except subprocess.CalledProcessError as error:
        if "HTTP 404" not in error.stderr:
            raise
        api(f"repos/{REPO}/labels", "POST", {
            "name": QUEUE_LABEL, "color": "29e9a2",
            "description": "Dependabot update selected by the weekly automatic dependency pass",
        })
    stage_critical(pulls("open", BASE))
    merge_dependencies()
    integration()


if __name__ == "__main__":
    main()
