# Continuous integration

## Workflows

| Workflow | Trigger | What it does |
|---|---|---|
| `.github/workflows/build.yml` | push to `main`, PRs, manual | Shared tests, then one matrix job per Minecraft target, then aggregate verification |
| `.github/workflows/release.yml` | tag `v*`, manual | Builds every target in one run and publishes the release |

The build matrix is generated from `versions.json` by the `targets` job, so
adding a Minecraft version adds a CI job automatically. Nothing about a version
is duplicated in workflow YAML.

Job layout:

```
targets ──► build (matrix: one job per Minecraft version, fail-fast: false)
   │                     │
shared                   ▼
(unit tests +      aggregate  (if: always())
 import check)      download artifacts
                    generate manifest.json
                    verify-builds.py         <- fails if a configured target produced no JAR
                    generate-version-table.py --check
```

`aggregate` runs with `if: always()` on purpose: if a target fails, the job
still runs and names the missing version instead of being skipped, so the
failure report is specific.

## Where CI runs

GitHub Actions is **billing-locked on `nglmercer/commandclient`**: every job
there is cancelled before it starts with

```
The job was not started because your account is locked due to a billing issue.
```

That is an account-level block; no workflow change fixes it. CI therefore runs
on the **`hernan-lc/commandclient`** fork, and `ci-status.json` records which
repository each result came from.

First green matrix there:
[run 33277870501](https://github.com/hernan-lc/commandclient/actions/runs/33277870501) —
all 14 Minecraft targets built, including 26.1/26.2 on Java 25 and 1.16.x on
Java 8, plus the shared tests and the Minecraft-import gate.

## Recording what CI verified

```bash
python3 scripts/fetch-ci-status.py --repo hernan-lc/commandclient
python3 scripts/generate-version-table.py
git commit -am "docs: record CI verification"
```

`--repo` matters: without it the script reads the origin remote, which is the
billing-locked repository. The generator marks a target CI verified only if
`ci-status.json` says a matrix job for it concluded `success`; a local build
can never set that column.

`ci-status.json` is committed, so the tables regenerate identically from a
clean checkout and `--check` behaves the same locally and on a runner. Refresh
it and regenerate whenever you want the docs to reflect a newer run.

## Verifying the workflows without runners

The YAML parses and the job graph is checked structurally, but neither proves
GitHub accepts it. The parts that do not need a runner:

```bash
# The matrix generator is plain Python; run the same code CI runs.
python3 - <<'PY'
import json
data = json.load(open("versions.json"))
mod = data["modVersion"]
print(json.dumps([{
    "id": k, "minecraft": s["minecraft"], "module": s["module"],
    "java": str(s["buildJava"]),
    "jarTask": data["buildFamilies"][s["buildFamily"]]["productionJarTask"],
    "artifact": f"commandapi-{mod}+mc{s['minecraft']}.jar",
} for k, s in data["versions"].items()], indent=2))
PY

# The aggregate job's steps, run locally against a real build:
./gradlew buildAllVersions
python3 scripts/generate-manifest.py
python3 scripts/verify-builds.py
python3 scripts/generate-version-table.py --check
```

Those four commands are exactly what `aggregate` runs, so a green local run
means the aggregate logic is sound even though the runner never started.
