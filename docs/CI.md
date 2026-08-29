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

## Current status: blocked by billing, not by the code

At the time of writing, **GitHub Actions cannot run on this repository**. Every
job is cancelled before it starts with:

```
The job was not started because your account is locked due to a billing issue.
```

Runs [33274408717](https://github.com/nglmercer/commandclient/actions/runs/33274408717)
and [33274491276](https://github.com/nglmercer/commandclient/actions/runs/33274491276)
both failed this way after ~6 seconds, with all jobs cancelled and zero steps
executed. That is an account-level block: no workflow change can fix it, and it
would happen to any workflow in the repository.

**Nothing in this project claims CI verification while that is true.** The
generated tables show `—` for every target's CI column, and
`docs/VERSIONS.md` says how many jobs never executed.

### Once billing is fixed

```bash
git push                                   # triggers the Build workflow
gh run watch                               # follow it
python3 scripts/fetch-ci-status.py         # record what CI actually verified
python3 scripts/generate-version-table.py  # tables now show real CI results
git commit -am "docs: record CI verification"
```

`fetch-ci-status.py` reads the latest run with the `gh` CLI and writes
`build/ci-status.json`. The table generator only marks a target CI verified if
that file says a matrix job for it concluded `success`. It cannot be talked into
it any other way — a local build never sets the CI column.

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
