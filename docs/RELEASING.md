# Releasing

A release contains one JAR per target in `versions.json` and `manifest.json`.
The tag must name `modVersion`; for example, version `1.3.2` uses tag `v1.3.2`.
The `v` prefix is cosmetic and may be upper or lower case, so `v1.3.2` and
`V1.3.2` release the same version. The version itself must match exactly:
tagging `v1.3.3` while `modVersion` is `1.3.2` is refused.

1. Bump `modVersion` in `versions.json` and finish source changes and tests.
2. Run `./gradlew :common:test buildAllVersions verifyBuilds versionTable`.
3. Check `build/distributions/manifest.json` and the JAR names against
   `versions.json`. Keep `runtimeVerified` false unless the exact version was
   exercised in Minecraft using `docs/RUNTIME_VERIFICATION.md`.
4. Commit the repaired source, manifest, and generated documentation; push the
   commit to the release branch.
5. Tag **that commit** with `v<modVersion>` and push the tag. The release workflow
   rebuilds from the tagged source and publishes those new artifacts. Never
   upload assets built from a different commit or reuse older release JARs.

The release workflow checks the tag/version match before building. A failed
build or artifact verification must stop publication. A release can report
build compatibility while still listing runtime verification as pending.

The workflow always checks out the tag it is releasing, so a manual run
(`gh workflow run release.yml -f tag=v1.3.2`) publishes artifacts built from
the tagged commit and not from whatever branch triggered it. It runs for a
pushed `v*`/`V*` tag, for a release published in the web UI, and on demand.

The published release is titled `Command API v<modVersion>` whatever the tag
is called; the release notes name the tag the artifacts were built from when
it differs from that canonical form.

## Separate server release

Run `./gradlew buildAllVersions collectServerArtifact verifyBuilds` before release.
The release workflow also builds/tests/verifies the server plugin and publishes
`build/server-distributions/*.jar` with its server manifest. Fabric and server
JARs install in different directories. Runtime compatibility remains tied to
explicit evidence, not CI compilation.

Server releases contain both `commandapi-server-legacy-<version>.jar` and
`commandapi-server-modern-<version>.jar`. The server manifest lists their
separate Java/API baselines and target ranges. Collection synchronizes its
output directory so the superseded single JAR cannot be accidentally released.
The packaging verifier requires both variants and inspects every class's
bytecode level, including bundled common classes. Users install exactly one.
