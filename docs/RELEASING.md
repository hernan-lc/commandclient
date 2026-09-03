# Releasing

A release publishes one JAR per supported Minecraft version, plus
`manifest.json`, to a GitHub release.

## The one rule

The tag must match `modVersion` in `versions.json`. Tag `v1.2.1` requires
`"modVersion": "1.3.1"`. The workflow checks this **before** building and stops
in seconds if they disagree, because the JARs it would produce are named after
`modVersion` — publishing `commandapi-1.2.0+mc26.2.jar` under a `v1.2.1` tag
would be a lie about what the artifacts are.

## Procedure

```bash
# 1. Bump the version in the manifest (the single source of truth).
#    Edit versions.json: "modVersion": "1.3.1"

# 2. Rebuild so the artifacts and generated tables carry the new version.
./gradlew buildAllVersions verifyBuilds versionTable

# 3. Commit.
git commit -am "release: 1.3.1"
git push

# 4. Tag that commit and push the tag. This triggers the Release workflow.
git tag v1.2.1
git push origin v1.2.1
```

The workflow then builds every configured target, verifies the artifacts, and
publishes them with a compatibility table in the notes. If any target fails,
nothing is published.

## Creating the release in the GitHub UI instead

This also works, but mind the order. Creating a release in the UI **creates its
tag**, which triggers the workflow — so the tag will point at whatever commit
you chose there. Make sure that commit already has the matching `modVersion`,
or the run stops at the tag check.

The workflow handles an already-existing release: it uploads the JARs into it
and rewrites the notes rather than failing on a duplicate tag name. So a
release created in the UI gets filled in by the run it triggered.

## Re-running a release

Re-running is safe: assets are uploaded with `--clobber`, so they are replaced
rather than duplicated.

One catch that is easy to miss: a tag-triggered run uses **the workflow file
from the tagged commit**, not from `main`. If you fix the release workflow, a
re-run of an old tag still uses the old, broken workflow. Move the tag onto the
fixed commit first:

```bash
git tag -f v1.2.1        # on the commit that has the fix
git push -f origin v1.2.1
```

## If the tag and version have already diverged

Either move the tag onto a commit whose `modVersion` matches, or bump
`modVersion` to the tag you want and re-tag:

```bash
# releasing 1.3.1 from a tree that still says 1.2.0
# edit versions.json -> 1.3.1
./gradlew buildAllVersions versionTable
git commit -am "release: 1.3.1" && git push
git tag -f v1.2.1 && git push -f origin v1.2.1
```

## Checklist

* `versions.json` `modVersion` equals the tag without the `v`
* `./gradlew buildAllVersions verifyBuilds` passes locally
* generated tables regenerated and committed (`versionTable`)
* the tag points at the commit holding all of the above
