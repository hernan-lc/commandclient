#!/usr/bin/env python3
"""Verify that the collected build output matches versions.json.

Checks, for every configured Minecraft target:
  * the Gradle module directory and build file exist;
  * the manifest lists an artifact and the file is present;
  * the artifact name contains the right Minecraft version and is unique;
  * the fabric.mod.json inside the JAR declares the right Minecraft version,
    Java requirement, loader requirement, mod id and entrypoint.

Usage: python3 scripts/verify-builds.py [--allow-missing 1.20.1 ...]
Exits non-zero and prints every problem found.
"""

import argparse
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSIONS_JSON = ROOT / "versions.json"
DIST = ROOT / "build" / "distributions"
MANIFEST = DIST / "manifest.json"

MOD_ID = "commandapi"
ENTRYPOINT = "com.commandapi.version.CommandApiMod"


class Report:
    def __init__(self):
        self.errors = []
        self.checks = 0

    def check(self, condition, message):
        self.checks += 1
        if not condition:
            self.errors.append(message)
        return condition


def read_mod_json(jar_path, report):
    try:
        with zipfile.ZipFile(jar_path) as jar:
            with jar.open("fabric.mod.json") as handle:
                return json.loads(handle.read().decode("utf-8"))
    except (KeyError, OSError, zipfile.BadZipFile, ValueError) as exc:
        report.check(False, f"{jar_path.name}: cannot read fabric.mod.json ({exc})")
        return None


def verify_jar(spec, artifact, mod_version, report):
    jar_path = DIST / artifact["file"]
    if not report.check(jar_path.is_file(),
                        f"{spec['minecraft']}: manifest lists {artifact['file']} but the file is missing"):
        return

    expected_name = f"commandapi-{mod_version}+mc{spec['minecraft']}.jar"
    report.check(artifact["file"] == expected_name,
                 f"{spec['minecraft']}: artifact is named {artifact['file']}, expected {expected_name}")
    report.check(f"mc{spec['minecraft']}" in artifact["file"],
                 f"{spec['minecraft']}: artifact name does not carry its Minecraft version")

    mod_json = read_mod_json(jar_path, report)
    if mod_json is None:
        return

    depends = mod_json.get("depends", {})
    report.check(mod_json.get("id") == MOD_ID,
                 f"{spec['minecraft']}: mod id is {mod_json.get('id')!r}, expected {MOD_ID!r}")
    report.check(mod_json.get("version") == f"{mod_version}+mc{spec['minecraft']}",
                 f"{spec['minecraft']}: fabric.mod.json version is {mod_json.get('version')!r}")
    report.check(depends.get("minecraft") == spec["minecraft"],
                 f"{spec['minecraft']}: fabric.mod.json declares minecraft "
                 f"{depends.get('minecraft')!r}")
    report.check(depends.get("java") == f">={spec['java']}",
                 f"{spec['minecraft']}: fabric.mod.json declares java {depends.get('java')!r}, "
                 f"expected '>={spec['java']}'")
    report.check(depends.get("fabricloader") == f">={spec['loader']}",
                 f"{spec['minecraft']}: fabric.mod.json declares fabricloader "
                 f"{depends.get('fabricloader')!r}, expected '>={spec['loader']}'")
    report.check(mod_json.get("entrypoints", {}).get("client") == [ENTRYPOINT],
                 f"{spec['minecraft']}: client entrypoint is "
                 f"{mod_json.get('entrypoints', {}).get('client')!r}")
    report.check(mod_json.get("environment") == "client",
                 f"{spec['minecraft']}: environment is {mod_json.get('environment')!r}, "
                 "expected 'client' for this client-side mod")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-missing", nargs="*", default=[], metavar="MC",
                        help="targets that may be absent from the manifest")
    args = parser.parse_args()

    report = Report()
    data = json.loads(VERSIONS_JSON.read_text(encoding="utf-8"))
    mod_version, versions = data["modVersion"], data["versions"]

    for key, spec in versions.items():
        module_dir = ROOT / "versions" / key
        report.check(module_dir.is_dir(), f"{key}: missing module directory {module_dir}")
        report.check((module_dir / "build.gradle").is_file(),
                     f"{key}: missing {module_dir / 'build.gradle'}")
        report.check(spec.get("module") == f"versions:{key}",
                     f"{key}: 'module' should be 'versions:{key}', got {spec.get('module')!r}")

    if not MANIFEST.is_file():
        print(f"error: {MANIFEST.relative_to(ROOT)} not found. "
              "Run ./gradlew buildAllVersions first.", file=sys.stderr)
        return 1

    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    report.check(manifest.get("modVersion") == mod_version,
                 f"manifest modVersion {manifest.get('modVersion')!r} != versions.json {mod_version!r}")

    by_minecraft = {}
    names = {}
    for artifact in manifest.get("artifacts", []):
        by_minecraft[artifact["minecraft"]] = artifact
        names.setdefault(artifact["file"], []).append(artifact["minecraft"])

    for name, owners in names.items():
        report.check(len(owners) == 1,
                     f"artifact name {name} is claimed by several targets: {', '.join(owners)}")

    for key, spec in versions.items():
        artifact = by_minecraft.get(spec["minecraft"])
        if artifact is None:
            if spec["minecraft"] in args.allow_missing:
                print(f"skipping {spec['minecraft']}: allowed to be missing")
                continue
            report.check(False, f"{spec['minecraft']}: configured target produced no artifact")
            continue
        verify_jar(spec, artifact, mod_version, report)

    unknown = set(by_minecraft) - {s["minecraft"] for s in versions.values()}
    for extra in sorted(unknown):
        report.check(False, f"manifest lists {extra}, which is not in versions.json")

    if report.errors:
        print(f"FAILED: {len(report.errors)} problem(s) in {report.checks} checks", file=sys.stderr)
        for error in report.errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    print(f"OK: {report.checks} checks passed for "
          f"{len(by_minecraft)} artifact(s) of Command API {mod_version}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
