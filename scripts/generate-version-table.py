#!/usr/bin/env python3
"""Generate docs/VERSIONS.md and the README version table.

Support status is reported at four independent levels, and each one is only
claimed when there is evidence for it:

  Configured      the target is declared in versions.json with a module
  Artifact        a JAR exists in the local build manifest
  CI verified     a real GitHub Actions run built it (build/ci-status.json,
                  written by scripts/fetch-ci-status.py)
  Runtime verified  someone launched Minecraft and exercised the API
                  (versions.json -> runtimeVerified)

A local build never implies CI success, and neither implies the mod was ever
run in the game.

Usage: python3 scripts/generate-version-table.py [--check]
"""

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSIONS_JSON = ROOT / "versions.json"
MANIFEST = ROOT / "build" / "distributions" / "manifest.json"
CI_STATUS = ROOT / "build" / "ci-status.json"
VERSIONS_DOC = ROOT / "docs" / "VERSIONS.md"
README = ROOT / "README.md"

GENERATED_BANNER = ("<!-- GENERATED FILE. DO NOT EDIT MANUALLY. "
                    "Run: python3 scripts/generate-version-table.py -->")
README_START = "<!-- SUPPORTED_VERSIONS_START -->"
README_END = "<!-- SUPPORTED_VERSIONS_END -->"

YES, NO = "✅", "❌"


def version_key(version):
    """Sort Minecraft versions semantically: 1.16.5 < 1.21.4 < 1.21.11 < 26.1."""
    return [(0, int(c)) if c.isdigit() else (1, c) for c in re.split(r"[.\-+]", version)]


def load(path):
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else None


def rows(versions, manifest, ci):
    built = {a["minecraft"]: a for a in (manifest or {}).get("artifacts", [])}
    ci_targets = (ci or {}).get("targets", {})
    for key in sorted(versions, key=version_key):
        spec = versions[key]
        yield spec, built.get(spec["minecraft"]), ci_targets.get(spec["minecraft"])


def status_cells(spec, artifact, ci_entry, mod_version):
    build_cell = YES if artifact else NO
    if ci_entry is None:
        # No CI status recorded at all.
        ci_cell = "—"
    elif ci_entry.get("verified"):
        ci_cell = YES
    elif ci_entry.get("conclusion") == "failure":
        ci_cell = NO
    else:
        # The job never executed (missing, cancelled, skipped). Not a failure of
        # the code, so it must not be rendered as one.
        ci_cell = "—"
    runtime_cell = YES if spec.get("runtimeVerified") else "—"
    name = artifact["file"] if artifact else f"commandapi-{mod_version}+mc{spec['minecraft']}.jar"
    return build_cell, ci_cell, runtime_cell, name


def versions_doc(data, manifest, ci):
    mod_version, versions = data["modVersion"], data["versions"]
    lines = [
        GENERATED_BANNER,
        "",
        "# Supported Minecraft versions",
        "",
        f"Command API `{mod_version}`. Every row is a target configured in "
        "[`versions.json`](../versions.json) with a module under `versions/`.",
        "",
        "## What the columns mean",
        "",
        "| Column | Meaning |",
        "|---|---|",
        "| **Artifact** | A JAR for this target exists in the local build manifest. |",
        "| **CI** | A real GitHub Actions run built this target. `❌` means it ran and failed; "
        "`—` means it never ran or no result was recorded. |",
        "| **Runtime** | Minecraft was launched with the mod and the API exercised. `—` means not done. |",
        "",
        "A local build says nothing about CI, and neither says the mod was ever "
        "run in the game. Nothing in this file is hand-written.",
        "",
    ]

    if manifest:
        lines += [f"Build manifest: `{manifest['modVersion']}`, "
                  f"{len(manifest.get('artifacts', []))} artifact(s).", ""]
    else:
        lines += ["No local build manifest found, so no target can claim an artifact. "
                  "Run `./gradlew buildAllVersions`.", ""]

    if ci:
        lines += [
            f"CI status: workflow **{ci['workflow']}** run "
            f"[{ci['runId']}]({ci['runUrl']}) on `{ci['branch']}` "
            f"({ci['runConclusion']}), recorded from commit `{ci['headSha'][:8]}`.",
            "",
        ]
        never_ran = sorted((mc for mc, t in ci.get("targets", {}).items()
                            if not t.get("verified") and t.get("conclusion") != "failure"),
                           key=version_key)
        if never_ran:
            lines += [
                f"In that run **{len(never_ran)} target job(s) never executed** "
                f"(status: {', '.join(sorted({t['conclusion'] for t in ci['targets'].values()}))}). "
                "They are shown as `—`, not as failures: the code was never built there.",
                "",
            ]
    else:
        lines += [
            "**No CI status recorded.** No target below is marked CI verified. "
            "Run `python3 scripts/fetch-ci-status.py` once GitHub Actions can execute.",
            "",
        ]

    lines += [
        "| Minecraft | Tier | Java | Loader | Build family | Adapter | Artifact | CI | Runtime | File |",
        "| --------- | :--: | ---: | ------ | ------------ | ------- | :------: | :-: | :-----: | ---- |",
    ]
    for spec, artifact, ci_entry in rows(versions, manifest, ci):
        build_cell, ci_cell, runtime_cell, name = status_cells(spec, artifact, ci_entry, mod_version)
        lines.append(
            f"| {spec['minecraft']} | {spec['tier']} | {spec['java']} | {spec['loader']} | "
            f"{spec['buildFamily']} | {spec['adapterFamily']} | {build_cell} | {ci_cell} | "
            f"{runtime_cell} | `{name}` |"
        )

    lines += ["", "## Support tiers", "", "| Tier | Meaning |", "|---|---|"]
    for tier, meaning in sorted(data["supportTiers"].items()):
        lines.append(f"| {tier} | {meaning} |")

    lines += ["", "## Build families", "", "| Family | Loom plugin | Mappings | Production JAR task |",
              "|---|---|---|---|"]
    for name, family in data["buildFamilies"].items():
        lines.append(f"| `{name}` | `{family['loomPlugin']}` | {family['mappings']} | "
                     f"`{family['productionJarTask']}` |")

    lines += ["", "## Adapter families", "", "| Family | Minecraft API used |", "|---|---|"]
    for name, adapter in data["adapterFamilies"].items():
        lines.append(f"| `{name}` | {adapter['description']} |")

    notes = [(s["minecraft"], s["notes"]) for s in versions.values() if s.get("notes")]
    if notes:
        lines += ["", "## Notes", ""]
        for mc, note in sorted(notes, key=lambda n: version_key(n[0])):
            lines.append(f"* **{mc}** — {note}")

    lines += [
        "",
        "## Building a single target",
        "",
        "```bash",
        *[f"./gradlew :{versions[k]['module']}:build" for k in sorted(versions, key=version_key)],
        "```",
        "",
        "See [ADDING_VERSION.md](ADDING_VERSION.md) to add a target and "
        "[BUILD.md](BUILD.md) for the build layout.",
        "",
    ]
    return "\n".join(lines)


def readme_block(data, manifest, ci):
    mod_version, versions = data["modVersion"], data["versions"]
    lines = [
        README_START,
        "<!-- Generated by scripts/generate-version-table.py. Do not edit by hand. -->",
        "",
        "| Minecraft | Tier | Java | Artifact built | CI verified | Runtime verified |",
        "|---|:--:|---:|:--:|:--:|:--:|",
    ]
    for spec, artifact, ci_entry in rows(versions, manifest, ci):
        build_cell, ci_cell, runtime_cell, _ = status_cells(spec, artifact, ci_entry, mod_version)
        lines.append(f"| {spec['minecraft']} | {spec['tier']} | {spec['java']} | "
                     f"{build_cell} | {ci_cell} | {runtime_cell} |")
    lines += [
        "",
        "`—` means not verified, not failed. See [docs/VERSIONS.md](docs/VERSIONS.md) "
        "for what each column means.",
        README_END,
    ]
    return "\n".join(lines)


def replace_readme_block(text, block):
    if README_START not in text or README_END not in text:
        raise SystemExit(f"{README} is missing the {README_START} / {README_END} markers.")
    return re.sub(re.escape(README_START) + ".*?" + re.escape(README_END),
                  lambda _: block, text, flags=re.DOTALL)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="fail instead of writing when files are out of date")
    args = parser.parse_args()

    data = load(VERSIONS_JSON)
    manifest = load(MANIFEST)
    ci = load(CI_STATUS)

    outputs = {
        VERSIONS_DOC: versions_doc(data, manifest, ci),
        README: replace_readme_block(README.read_text(encoding="utf-8"),
                                     readme_block(data, manifest, ci)),
    }

    stale = [p for p, content in outputs.items()
             if not p.is_file() or p.read_text(encoding="utf-8") != content]

    if args.check:
        for path in stale:
            print(f"out of date: {path.relative_to(ROOT)}", file=sys.stderr)
        if stale:
            print("Run: python3 scripts/generate-version-table.py", file=sys.stderr)
            return 1
        print("Generated version tables are up to date.")
        return 0

    for path, content in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        print(f"wrote {path.relative_to(ROOT)}")
    if not manifest:
        print(f"note: {MANIFEST.relative_to(ROOT)} not found; no target claims an artifact.",
              file=sys.stderr)
    if not ci:
        print(f"note: {CI_STATUS.relative_to(ROOT)} not found; no target claims CI verification.",
              file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
