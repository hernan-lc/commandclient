# Build guide

## Requirements

* **JDK 21** for the legacy targets (Minecraft ≤ 1.21.11)
* **JDK 25** for the modern targets (Minecraft 26.x). Gradle will download it
  automatically if it is missing — the foojay toolchain resolver is enabled in
  `settings.gradle`.
* **Python 3** for the generator and verification scripts
* Internet access on the first build (Minecraft, Loom, Fabric artifacts)

The Gradle wrapper pins **Gradle 9.6.1**; use `./gradlew`, never a system Gradle.

## Commands

```bash
./gradlew buildAllVersions          # every target in versions.json + collected artifacts
./gradlew :versions:26.2:build      # one target (see docs/VERSIONS.md for the list)
./gradlew :common:test              # unit tests for the shared code
./gradlew printVersions             # what is configured, with families and tiers
./gradlew collectArtifacts          # copy JARs + write build/distributions/manifest.json
./gradlew versionTable              # regenerate docs/VERSIONS.md and the README table
./gradlew verifyBuilds              # check the collected artifacts against versions.json
```

Every JAR is named `commandapi-<mod-version>+mc<minecraft-version>.jar`, so
artifacts from different targets can never be confused.

## Two build families

Minecraft changed how it ships in 26.1: Mojang stopped publishing obfuscated
jars, so there are no `client_mappings` to remap against. That is a real fork in
the build, not a version number difference, and `versions.json` names it per
target.

| | `legacy-remapped` | `modern-unobfuscated` |
|---|---|---|
| Minecraft | ≤ 1.21.11 | ≥ 26.1 |
| Loom plugin id | `fabric-loom` | `net.fabricmc.fabric-loom` |
| Mappings | `loom.officialMojangMappings()` | none — asking for them is an error |
| Mod dependencies | `modImplementation` (Loom remaps them) | `implementation` (nothing to remap) |
| Production JAR | `remapJar` | `jar` — **no remap task exists** |

Both families use the **same Loom version** (declared once in `versions.json`);
only the plugin id differs, which is what selects the pipeline. Gradle cannot
put two versions of one plugin on a build classpath, and `settings.gradle`
fails loudly if the families ever disagree on the version.

Nothing in the build assumes `remapJar`. `collectArtifacts` asks each target's
family which task produces its JAR.

## Layout

```
versions.json                     single source of truth for every target
settings.gradle                   turns each versions.json entry into a Gradle module
build.gradle                      orchestration: buildAllVersions, collectArtifacts, ...
gradle/version-module.gradle      shared build logic for every version module
common/                           version independent code + unit tests
adapters/loader-entrypoint/       the Fabric entrypoint, shared by every target
adapters/<family>/                one MinecraftBridgeImpl per Minecraft API generation
templates/fabric.mod.json         expanded per target at build time
versions/<mc>/build.gradle        four lines: the Loom plugin id + the shared script
scripts/                          manifest, version table, CI status, verification
```

A version module contains **no Java source**. It names a build family and an
adapter family in `versions.json`, and the shared script wires up the rest.

## Java levels

`versions.json` records two different things per target:

| Field | Meaning |
|---|---|
| `java` | bytecode level the target requires (`options.release`, and `depends.java` in `fabric.mod.json`) |
| `buildJava` | JDK toolchain that compiles it |

They are not the same. Minecraft 1.16.5 needs Java 8 *bytecode*, but Loom needs
a modern JDK, so it is built with JDK 21 and `--release 8`. Verified in the
shipped JARs: 1.16.x classes are major version 52 (Java 8), 1.18.2–1.20.4 are
61 (Java 17), 1.20.6–1.21.11 are 65 (Java 21), 26.x are 69 (Java 25).

`common/` is compiled at the **oldest** level still supported (Java 8 today), so
shared code cannot accidentally use an API that breaks the 1.16.x targets.

## Fabric API

Not used, not depended on. The mod needs only Fabric Loader
(`ClientModInitializer`, `FabricLoader`), so `fabric.mod.json` declares no
Fabric API dependency and `verify-builds.py` fails the build if one appears.

## Verification

```bash
./gradlew verifyBuilds
```

`scripts/verify-builds.py` opens each collected JAR and checks the
`fabric.mod.json` inside it (mod id, version, Minecraft version, loader
requirement, Java requirement, client entrypoint, no Fabric API dependency),
that each module applies the plugin id its build family requires, that the JAR
came from the family's production task, that artifact names are unique and carry
their Minecraft version, and that every configured target produced a file.

Compilation is not runtime proof — see [RUNTIME_VERIFICATION.md](RUNTIME_VERIFICATION.md).
