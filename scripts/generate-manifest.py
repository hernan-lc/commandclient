#!/usr/bin/env python3
"""Write build/distributions/manifest.json from the JARs present in that directory.

`./gradlew collectArtifacts` writes the same manifest for local builds. This
script is for CI, where the artifacts of the per-version matrix jobs are
downloaded into build/distributions instead of being built in one place.
Only targets whose JAR is actually present are listed.

Usage: python3 scripts/generate-manifest.py [--dir build/distributions]
"""

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dir", default=str(ROOT / "build" / "distributions"),
                        help="directory holding the collected JARs")
    args = parser.parse_args()

    dist = Path(args.dir)
    if not dist.is_dir():
        print(f"error: {dist} does not exist", file=sys.stderr)
        return 1

    data = json.loads((ROOT / "versions.json").read_text(encoding="utf-8"))
    mod_version, versions = data["modVersion"], data["versions"]

    artifacts = []
    missing = []
    for key in sorted(versions):
        spec = versions[key]
        jar = dist / f"commandapi-{mod_version}+mc{spec['minecraft']}.jar"
        if not jar.is_file():
            missing.append(spec["minecraft"])
            continue
        artifacts.append({
            "minecraft": spec["minecraft"],
            "java": spec["java"],
            "loader": spec["loader"],
            "buildFamily": spec["buildFamily"],
            "adapterFamily": spec["adapterFamily"],
            "tier": spec["tier"],
            "jarTask": data["buildFamilies"][spec["buildFamily"]]["productionJarTask"],
            "file": jar.name,
            "size": jar.stat().st_size,
        })

    manifest = {
        "modVersion": mod_version,
        "generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "artifacts": artifacts,
    }
    (dist / "manifest.json").write_text(json.dumps(manifest, indent=4) + "\n", encoding="utf-8")

    print(f"wrote {dist / 'manifest.json'} with {len(artifacts)} artifact(s)")
    if missing:
        print(f"missing artifacts for: {', '.join(missing)}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
