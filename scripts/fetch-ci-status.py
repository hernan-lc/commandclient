#!/usr/bin/env python3
"""Record what GitHub Actions actually verified, into ci-status.json.

The documentation generator refuses to claim CI success on its own: it only
reports a target as CI verified if this file says a real workflow run built it.

The file is committed, so the generated tables are reproducible from a clean
checkout and `--check` gives the same answer locally and in CI. It is the
evidence behind every CI claim in the docs, so it belongs in the repository
rather than in an untracked build directory.

Requires the `gh` CLI to be installed and authenticated.

Usage: python3 scripts/fetch-ci-status.py [--workflow Build] [--branch main]
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "ci-status.json"


def gh(*args):
    result = subprocess.run(["gh", *args], capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f"gh {' '.join(args)} failed")
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workflow", default="Build")
    parser.add_argument("--branch", default="main")
    args = parser.parse_args()

    versions = json.loads((ROOT / "versions.json").read_text(encoding="utf-8"))["versions"]

    try:
        runs = json.loads(gh("run", "list", "--workflow", args.workflow, "--branch", args.branch,
                             "--limit", "1", "--json",
                             "databaseId,conclusion,status,headSha,url,createdAt"))
    except (RuntimeError, FileNotFoundError) as exc:
        print(f"error: could not query GitHub Actions ({exc})", file=sys.stderr)
        return 1

    if not runs:
        print(f"error: no runs found for workflow {args.workflow!r} on {args.branch!r}", file=sys.stderr)
        return 1

    run = runs[0]
    jobs = json.loads(gh("run", "view", str(run["databaseId"]), "--json", "jobs"))["jobs"]

    # Matrix jobs are named "Minecraft <version>".
    verified = {}
    for spec in versions.values():
        mc = spec["minecraft"]
        job = next((j for j in jobs if j["name"].strip() == f"Minecraft {mc}"), None)
        verified[mc] = {
            "conclusion": job["conclusion"] if job else "missing",
            "verified": bool(job and job["conclusion"] == "success"),
        }

    aggregate = next((j for j in jobs if j["name"].strip() == "Collect and verify"), None)

    status = {
        "workflow": args.workflow,
        "branch": args.branch,
        "runId": run["databaseId"],
        "runUrl": run["url"],
        "runConclusion": run["conclusion"],
        "runStatus": run["status"],
        "headSha": run["headSha"],
        "createdAt": run["createdAt"],
        "aggregateConclusion": aggregate["conclusion"] if aggregate else "missing",
        "targets": verified,
    }

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(status, indent=4) + "\n", encoding="utf-8")

    passed = sum(1 for v in verified.values() if v["verified"])
    print(f"wrote {OUT.relative_to(ROOT)}: run {run['databaseId']} ({run['conclusion']}), "
          f"{passed}/{len(verified)} targets CI verified")
    return 0


if __name__ == "__main__":
    sys.exit(main())
