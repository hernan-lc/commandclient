# Releasing

A release contains one JAR per target in `versions.json` and `manifest.json`.
The tag must match `modVersion`; for example, version `1.3.2` uses tag `v1.3.2`.

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
