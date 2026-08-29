<!-- GENERATED FILE. DO NOT EDIT MANUALLY. Run: python3 scripts/generate-version-table.py -->

# Supported Minecraft versions

Command API `1.2.1`. Every row is a target configured in [`versions.json`](../versions.json) with a module under `versions/`.

## What the columns mean

| Column | Meaning |
|---|---|
| **Artifact** | A JAR for this target exists in the local build manifest. |
| **CI** | A real GitHub Actions run built this target. `❌` means it ran and failed; `—` means it never ran or no result was recorded. |
| **Runtime** | Minecraft was launched with the mod and the API exercised. `—` means not done. |

A local build says nothing about CI, and neither says the mod was ever run in the game. Nothing in this file is hand-written.

Build manifest: `1.2.1`, 14 artifact(s).

CI status: workflow **Build** run [33278509806](https://github.com/hernan-lc/commandclient/actions/runs/33278509806) on `main` (success), recorded from commit `5fdc9e8d`.

| Minecraft | Tier | Java | Loader | Build family | Adapter | Artifact | CI | Runtime | File |
| --------- | :--: | ---: | ------ | ------------ | ------- | :------: | :-: | :-----: | ---- |
| 1.16.1 | D | 8 | 0.14.24 | legacy-remapped | legacy-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc1.16.1.jar` |
| 1.16.5 | C | 8 | 0.14.24 | legacy-remapped | legacy-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc1.16.5.jar` |
| 1.18.2 | C | 17 | 0.16.14 | legacy-remapped | legacy-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc1.18.2.jar` |
| 1.19.2 | C | 17 | 0.16.14 | legacy-remapped | signed-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc1.19.2.jar` |
| 1.19.4 | D | 17 | 0.16.14 | legacy-remapped | network-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc1.19.4.jar` |
| 1.20.1 | C | 17 | 0.16.14 | legacy-remapped | network-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc1.20.1.jar` |
| 1.20.4 | D | 17 | 0.16.14 | legacy-remapped | network-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc1.20.4.jar` |
| 1.20.6 | C | 21 | 0.16.14 | legacy-remapped | network-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc1.20.6.jar` |
| 1.21.1 | B | 21 | 0.16.14 | legacy-remapped | network-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc1.21.1.jar` |
| 1.21.4 | B | 21 | 0.16.14 | legacy-remapped | network-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc1.21.4.jar` |
| 1.21.8 | B | 21 | 0.19.3 | legacy-remapped | network-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc1.21.8.jar` |
| 1.21.11 | B | 21 | 0.19.3 | legacy-remapped | network-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc1.21.11.jar` |
| 26.1 | B | 25 | 0.19.5 | modern-unobfuscated | network-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc26.1.jar` |
| 26.2 | A | 25 | 0.19.5 | modern-unobfuscated | network-chat | ✅ | ✅ | — | `commandapi-1.2.1+mc26.2.jar` |

## Support tiers

| Tier | Meaning |
|---|---|
| A | Latest stable Minecraft. Supported first after a Minecraft update. |
| B | Current/recent ecosystem versions that mods are actively built for. |
| C | Historical modding anchors with lasting community use. |
| D | Best effort. Kept while it costs nothing; no guarantee for arbitrary patches. |

## Build families

| Family | Loom plugin | Mappings | Production JAR task |
|---|---|---|---|
| `legacy-remapped` | `fabric-loom` | official-mojang | `remapJar` |
| `modern-unobfuscated` | `net.fabricmc.fabric-loom` | none | `jar` |

## Adapter families

| Family | Minecraft API used |
|---|---|
| `legacy-chat` | LocalPlayer.chat(String) sends both chat and commands. Replaced in 1.19 by signed chat. |
| `signed-chat` | 1.19-1.19.2 signed chat: LocalPlayer.chatSigned / commandSigned with a nullable preview component. |
| `network-chat` | 1.19.3+ splits sending: ClientPacketListener.sendChat / sendCommand. The same Mojang-mapped names still apply on unobfuscated 26.x, so this family covers both build families. |

## Notes

* **1.16.5** — Fabric Loader 0.15+ requires Java 17, so Java 8 targets pin the 0.14.x line.
* **1.21.11** — Last obfuscated release; the boundary marker for the legacy build family.
* **26.1** — First unobfuscated release; kept as the boundary marker for the modern build family.

## Building a single target

```bash
./gradlew :versions:1.16.1:build
./gradlew :versions:1.16.5:build
./gradlew :versions:1.18.2:build
./gradlew :versions:1.19.2:build
./gradlew :versions:1.19.4:build
./gradlew :versions:1.20.1:build
./gradlew :versions:1.20.4:build
./gradlew :versions:1.20.6:build
./gradlew :versions:1.21.1:build
./gradlew :versions:1.21.4:build
./gradlew :versions:1.21.8:build
./gradlew :versions:1.21.11:build
./gradlew :versions:26.1:build
./gradlew :versions:26.2:build
```

See [ADDING_VERSION.md](ADDING_VERSION.md) to add a target and [BUILD.md](BUILD.md) for the build layout.
