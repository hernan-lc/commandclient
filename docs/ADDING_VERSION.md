# Adding a Minecraft version

Adding a target is a configuration change. A version module has no Java source:
it names a **build family** (how it is compiled and packaged) and an **adapter
family** (which Minecraft chat API it uses), both defined in `versions.json`.

Do not add several versions at once — port one, get it green, then move on.

## 1. Add the entry to `versions.json`

```json
"1.21.12": {
  "minecraft": "1.21.12",
  "loader": "0.19.3",
  "java": 21,
  "buildJava": 21,
  "buildFamily": "legacy-remapped",
  "adapterFamily": "network-chat",
  "tier": "B",
  "runtimeVerified": false,
  "module": "versions:1.21.12"
}
```

Verify every number instead of copying:

* **`java`** — the bytecode level the release requires. Read it from the Mojang
  version manifest rather than assuming:
  ```bash
  curl -s https://launchermeta.mojang.com/mc/game/version_manifest_v2.json \
    | python3 -c "import json,sys,urllib.request as u; m=json.load(sys.stdin); \
        url={v['id']:v['url'] for v in m['versions']}['1.21.12']; \
        d=json.load(u.urlopen(url)); print('java', d['javaVersion']['majorVersion'], \
        'obfuscated' if 'client_mappings' in d['downloads'] else 'UNOBFUSCATED')"
  ```
* **`buildFamily`** — that same command tells you. If the release has
  `client_mappings` it is `legacy-remapped`; if it has none, Mojang ships it
  unobfuscated and it is `modern-unobfuscated`.
* **`loader`** — a Fabric Loader version that supports the release. Note that
  Loader 0.15+ requires Java 17, so Java 8 targets must stay on the 0.14.x line.
* **`buildJava`** — the JDK that compiles it. Keep it at the newest JDK the
  other targets in the family already use.
* **`tier`** — see the support policy in [VERSIONS.md](VERSIONS.md).

## 2. Create the module

```bash
mkdir -p versions/1.21.12
cp versions/1.21.11/build.gradle versions/1.21.12/build.gradle
```

The file is four lines. Its only job is applying the Loom plugin id that matches
the build family:

| Build family | Plugin id |
|---|---|
| `legacy-remapped` | `fabric-loom` |
| `modern-unobfuscated` | `net.fabricmc.fabric-loom` |

Copy from a module in the **same family**, or `verify-builds.py` will fail with
a mismatch.

## 3. Build it

```bash
./gradlew :versions:1.21.12:build
```

If it compiles, the existing adapter family fits and you are done with code.

## 4. Only if it does not compile: pick or add an adapter family

A compile error in `adapters/<family>/MinecraftBridgeImpl.java` means this
release changed the chat API. Find out what it actually offers instead of
guessing — introspect the remapped jar Loom just produced:

```bash
JAR=$(find ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.12-* -name '*.jar' | head -1)
javap -cp "$JAR" net.minecraft.client.player.LocalPlayer | grep -iE "chat|command"
javap -cp "$JAR" net.minecraft.client.multiplayer.ClientPacketListener | grep -iE "public.*(chat|command)"
```

Known generations:

| Family | Versions | How a message is sent |
|---|---|---|
| `legacy-chat` | 1.16.x – 1.18.2 | `player.chat(text)`, commands keep the `/` |
| `signed-chat` | 1.19 – 1.19.2 | `player.chatSigned(text, null)` / `commandSigned(cmd, null)` |
| `network-chat` | 1.19.3 – 26.x | `player.connection.sendChat(text)` / `sendCommand(cmd)` |

If it matches one, set `adapterFamily` to it. If the API is genuinely new, add
`adapters/<new-family>/src/main/java/com/commandapi/version/MinecraftBridgeImpl.java`,
register it under `adapterFamilies` in `versions.json`, and point the version at
it. For a one-off quirk, set `"adapterFamily": "custom"` and put the class in
`versions/<id>/src/main/java/`.

A new or custom family must also keep the `/commandapi` chat commands working:
add a mixin that intercepts the generation's outgoing command path and calls
`CommandApiCommands.dispatchCommand` (or `dispatchChat` when the slash is still
attached), cancelling the send when it returns true — see the three existing
mixins. Ship its config as `commandapi.mixins.json`: for built-in families that
means `adapters/<family>/src/main/resources/commandapi.mixins.json` (picked up
automatically); for `custom` put it in `versions/<id>/src/main/resources`.
`fabric.mod.json` already lists that file name, and Loom generates the refmap.

Never branch on a Minecraft version string in shared code — the module you are
in *is* the branch.

## 5. Inspect the generated metadata

```bash
unzip -p versions/1.21.12/build/libs/commandapi-*+mc1.21.12.jar fabric.mod.json
```

Check `version`, `depends.minecraft`, `depends.fabricloader`, `depends.java`,
and that no Fabric API dependency appeared.

## 6. Make sure nothing else broke

```bash
./gradlew buildAllVersions
./gradlew verifyBuilds
```

A new target must not break an existing one.

## 7. Regenerate the tables

```bash
python3 scripts/generate-version-table.py
```

`docs/VERSIONS.md` and the README block are generated — never hand-edit them.
The new row will show an artifact but `—` for CI and runtime, which is correct:
neither has happened yet.

## 8. Commit

* the `versions.json` entry
* `versions/<version>/build.gradle`
* any new adapter family
* the regenerated `docs/VERSIONS.md` and `README.md`

CI picks the target up automatically: the matrix is generated from
`versions.json`, and the aggregate job fails if it produces no JAR.

## 9. Runtime verification

Building is not proof it works. If the target introduces a new adapter family,
run the checklist in [RUNTIME_VERIFICATION.md](RUNTIME_VERIFICATION.md) before
claiming support, then set `runtimeVerified`.
