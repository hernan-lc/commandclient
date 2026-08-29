# Build guide

## Requirements

* JDK 21 (the JDK that compiles every target today — see *Java levels* below)
* Python 3 for the generator/verification scripts
* Internet access on the first build (Minecraft, Loom and Fabric artifacts)

The Gradle wrapper pins Gradle 8.10.2; use `./gradlew`, not a system Gradle.

## Commands

```bash
./gradlew buildAllVersions        # every target in versions.json + collected artifacts
./gradlew :versions:1.16.1:build  # one target (see docs/VERSIONS.md for the full list)
./gradlew :common:test            # unit tests for the shared code
./gradlew printVersions           # what is configured
./gradlew collectArtifacts        # copy JARs + write build/distributions/manifest.json
./gradlew versionTable            # regenerate docs/VERSIONS.md and the README table
./gradlew verifyBuilds            # check the collected artifacts against versions.json
```

Output:

```
build/distributions/
├── commandapi-1.1.0+mc1.16.1.jar
├── commandapi-1.1.0+mc1.19.4.jar
├── commandapi-1.1.0+mc1.20.1.jar
├── commandapi-1.1.0+mc1.20.4.jar
└── manifest.json
```

Every JAR is named `commandapi-<mod-version>+mc<minecraft-version>.jar`, so
artifacts from different targets can never be confused.

## Layout

```
versions.json                     single source of truth for every target
settings.gradle                   turns each versions.json entry into a Gradle module
build.gradle                      orchestration: buildAllVersions, collectArtifacts, ...
gradle/fabric-version-module.gradle   shared build logic for every version module
common/                           version independent code + unit tests
templates/fabric.mod.json         expanded per target at build time
versions/<mc>/build.gradle        two lines: apply Loom, apply the shared logic
versions/<mc>/src/...             CommandApiMod + MinecraftBridgeImpl for that version
scripts/                          manifest, version table and verification scripts
```

`common/` is compiled two ways: as its own module (so it can be unit tested
without Loom or Minecraft) and as an extra source directory of each version
module (so its classes land in every JAR). Loom remaps only a module's own
output, and duplicating a dozen shared classes per target is cheaper than
wiring a nested project dependency through `remapJar`.

## Mappings

The project uses **official Mojang mappings** (`loom.officialMojangMappings()`)
for every version. There is no Yarn configuration; the old unused
`yarn_mappings` property was removed so the build metadata cannot mislead.

## Java levels

`versions.json` records two different things per target:

| Field | Meaning |
|---|---|
| `java` | bytecode level the target requires (`options.release`, and `depends.java` in `fabric.mod.json`) |
| `buildJava` | JDK toolchain that compiles it |

They are not the same: Minecraft 1.16.1 needs Java 8 *bytecode*, but a JDK 21
toolchain produces it with `--release 8`, and Loom itself requires a modern
JDK. CI installs `buildJava` for each matrix entry. Verified with the shipped
JARs: 1.16.1 classes are major version 52 (Java 8), 1.19.4 classes are 61
(Java 17).

## Configuration

`gradle.properties` holds build settings only. Runtime settings live in
`config/commandapi.json` inside your Minecraft directory — see
[API.md](API.md). Mod metadata (`fabric.mod.json`) is generated from
`templates/fabric.mod.json`; never edit a generated file inside `build/`.

## Verification

`scripts/verify-builds.py` opens each collected JAR and checks that the
`fabric.mod.json` inside it declares the expected mod id, version, Minecraft
version, loader requirement, Java requirement and client entrypoint, that
artifact names are unique and carry their Minecraft version, and that every
configured target actually produced a file. CI runs it after every build.
