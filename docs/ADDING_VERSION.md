# Adding a Minecraft version

Adding a target is a configuration change plus a small adapter. Shared code
should not need to change; if it does, that logic probably belongs behind
`MinecraftBridge`.

Do not add several versions at once — port one, get it green, then move on.

## 1. Add the entry to `versions.json`

```json
"1.20.6": {
  "minecraft": "1.20.6",
  "loader": "0.15.11",
  "fabricApi": "0.100.4+1.20.6",
  "fabricApiModId": "fabric-api",
  "java": 21,
  "buildJava": 21,
  "module": "versions:1.20.6"
}
```

* `java` — bytecode level this Minecraft version requires (17 since 1.18, 21 since 1.20.5).
* `buildJava` — JDK that compiles it; keep it at the newest JDK all targets share unless Loom forces otherwise.
* Treat these numbers as a starting point. They count as correct only once the module builds.

## 2. Create the module

```bash
mkdir -p versions/1.20.6/src/main/java/com/commandapi/version
cp versions/1.20.1/build.gradle versions/1.20.6/build.gradle
```

`build.gradle` is version independent — it just applies Loom and
`gradle/fabric-version-module.gradle`.

## 3. Copy the nearest adapter

```bash
cp versions/1.20.1/src/main/java/com/commandapi/version/*.java \
   versions/1.20.6/src/main/java/com/commandapi/version/
```

`CommandApiMod` is the Fabric entrypoint and rarely changes.
`MinecraftBridgeImpl` is where Minecraft API differences live.

## 4. Fix the adapter until it compiles

Only `MinecraftBridgeImpl` should need edits. Known differences so far:

| Versions | Sending chat / commands |
|---|---|
| 1.16.x | `player.chat(text)` — commands keep their leading `/` |
| 1.19.3+ | `player.connection.sendChat(text)` / `sendCommand(text without '/')` |

Never branch on the Minecraft version inside shared code; the module you are
in *is* the branch.

## 5. Build it

```bash
./gradlew :versions:1.20.6:build
```

## 6. Inspect the generated metadata

```bash
unzip -p versions/1.20.6/build/libs/commandapi-*+mc1.20.6.jar fabric.mod.json
```

Check `version`, `depends.minecraft`, `depends.fabricloader` and `depends.java`.

## 7. Make sure nothing else broke

```bash
./gradlew buildAllVersions
```

A new target must not break an existing one.

## 8. Verify and regenerate the tables

```bash
./gradlew verifyBuilds
python3 scripts/generate-version-table.py
```

`docs/VERSIONS.md` and the README block are generated — never hand-edit them.

## 9. Commit

* `versions.json` entry
* `versions/<version>/` module and adapter
* regenerated `docs/VERSIONS.md` and `README.md`
* any shared change the port genuinely required

CI picks the new target up automatically: the build matrix is generated from
`versions.json`, and the aggregate job fails if the target produces no JAR.
